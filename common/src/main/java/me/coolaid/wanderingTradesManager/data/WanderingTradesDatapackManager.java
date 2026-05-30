package me.coolaid.wanderingTradesManager.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import me.coolaid.wanderingTradesManager.parser.HeadCommandParser;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WanderingTradesDatapackManager {
    private static final List<String> ADD_TRADE_RESOURCE_PATHS = List.of(
            "data/wandering_trades/function/add_trade.mcfunction",
            "data/wandering_trades/functions/add_trade.mcfunction"
    );
    private static final List<String> PROVIDE_TRADE_FUNCTION_NAMES = List.of(
            "provide_hermit_trades.mcfunction",
            "provide_block_trades.mcfunction"
    );
    private static final String PACK_MCMETA = "pack.mcmeta";
    private static final String OVERLAYS_KEY = "overlays";
    private static final Pattern TRADE_INDEX_PATTERN = Pattern.compile("execute\\s+if\\s+score\\s+@s\\s+wt_tradeIndex\\s+matches\\s+(\\d+)");
    private static final Pattern RANDOM_RANGE_PATTERN = Pattern.compile("(?m)(^\\s*(?!#)[^\\r\\n]*\\bwt_tradeIndex\\b[^\\r\\n]*\\brandom\\s+value\\s+)(\\d+)\\.\\.(\\d+)");
    private static final Pattern ITEM_NAME_ASSIGNMENT_PATTERN = Pattern.compile("(\"minecraft:(?:item_name|custom_name)\"\\s*:\\s*)(?:'[^']*'|\"[^\"]*\")");
    private static final Pattern TEXTURE_ASSIGNMENT_PATTERN = Pattern.compile("(value\\s*:\\s*\")([A-Za-z0-9+/=]+)(\")");
    private static final String TEMP_PACK_PREFIX = "wtm_edit_";
    private static final String TEMP_PACK_SUFFIX = ".wtm-tmp";

    private DatapackScanResult lastScan = DatapackScanResult.empty();

    public DatapackScanResult refresh(MinecraftServer server) {
        return refresh(server.getWorldPath(LevelResource.DATAPACK_DIR), serverDataPackFormat());
    }

    public DatapackScanResult refresh(Path datapacksDirectory) {
        return refresh(datapacksDirectory, serverDataPackFormat());
    }

    private DatapackScanResult refresh(Path datapacksDirectory, PackFormat packFormat) {
        List<Path> matchingPacks = new ArrayList<>();
        LinkedHashMap<String, CustomHead> heads = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        if (datapacksDirectory == null) {
            lastScan = new DatapackScanResult(null, matchingPacks, heads.values().stream().toList(), List.of("World datapacks directory is not available yet"));
            return lastScan;
        }

        if (!Files.isDirectory(datapacksDirectory)) {
            lastScan = new DatapackScanResult(datapacksDirectory, matchingPacks, heads.values().stream().toList(), List.of("World datapacks directory does not exist: " + datapacksDirectory));
            return lastScan;
        }

        cleanStaleTempPacks(datapacksDirectory, warnings);

        try (var stream = Files.list(datapacksDirectory)) {
            for (Path pack : stream.sorted(WanderingTradesDatapackManager::compareCandidates).toList()) {
                if (isTemporaryPack(pack)) {
                    continue;
                }
                scanPack(pack, packFormat, matchingPacks, heads, warnings);
            }
        } catch (IOException e) {
            warnings.add("Failed to list datapacks in " + datapacksDirectory + ": " + e.getMessage());
        }

        lastScan = new DatapackScanResult(datapacksDirectory, matchingPacks, heads.values().stream().toList(), warnings);
        return lastScan;
    }

    private static PackFormat serverDataPackFormat() {
        return SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
    }

    public DatapackEditResult addHead(String requestedName, String textureValue) {
        if (lastScan.datapacksDirectory() == null) {
            return DatapackEditResult.failure(message("open_world_before_add"));
        }

        String name = cleanName(requestedName);
        String texture;
        try {
            CustomHead parsed = HeadCommandParser.parseBase64String(textureValue);
            texture = parsed.textureValue();
            if (name.isBlank() || name.equals("Custom Head")) {
                name = parsed.name();
            }
        } catch (IllegalArgumentException e) {
            return DatapackEditResult.failure(message("invalid_texture"));
        }

        for (CustomHead head : lastScan.heads()) {
            if (head.name().equalsIgnoreCase(name)) {
                return DatapackEditResult.failure(message("duplicate_name", name));
            }
            if (head.textureValue().equals(texture)) {
                return DatapackEditResult.failure(message("duplicate_texture"));
            }
        }

        Optional<EditableFunction> target = findPrimaryEditableFunction();
        if (target.isEmpty()) {
            return DatapackEditResult.failure(message("no_editable_function"));
        }

        String finalName = name;
        String finalTexture = texture;
        EditableFunction editableFunction = target.get();
        try {
            editFunction(editableFunction, content -> {
                int nextIndex = nextTradeIndex(content);
                return appendTradeEntry(content, generateTradeEntry(nextIndex, finalName, finalTexture));
            });
            refresh(lastScan.datapacksDirectory());
            return DatapackEditResult.success(message(ChatFormatting.GREEN, "added_head", finalName));
        } catch (IOException e) {
            return DatapackEditResult.failure(message("add_failed", e.getMessage()));
        }
    }

    public DatapackEditResult updateHead(CustomHead original, String requestedName, String textureValue) {
        if (original == null) {
            return DatapackEditResult.failure(message("no_head_selected"));
        }

        String name = cleanName(requestedName);
        if (name.isBlank()) {
            return DatapackEditResult.failure(message("blank_name"));
        }

        String texture;
        try {
            texture = HeadCommandParser.parseBase64String(textureValue).textureValue();
        } catch (IllegalArgumentException e) {
            return DatapackEditResult.failure(message("invalid_texture"));
        }

        if (name.equals(original.name()) && texture.equals(original.textureValue())) {
            return DatapackEditResult.unchanged(message("no_changes"));
        }

        Optional<EditableFunction> target = editableFunctionFor(original);
        if (target.isEmpty()) {
            return DatapackEditResult.failure(message("head_file_missing"));
        }

        EditableFunction editableFunction = target.get();
        try {
            boolean changed = editFunction(editableFunction, content -> {
                Optional<String> updated = replaceEntry(content, original.tradeIndex(), entry -> updateEntry(entry, original.tradeIndex(), name, texture));
                if (updated.isEmpty()) {
                    throw new IOException("Trade #" + original.tradeIndex() + " was not found");
                }
                return updated.get();
            });
            if (!changed) {
                return DatapackEditResult.unchanged(message("no_changes"));
            }
            refresh(lastScan.datapacksDirectory());
            return DatapackEditResult.success(message(ChatFormatting.GREEN, "updated_head", name));
        } catch (IOException e) {
            return DatapackEditResult.failure(message("update_failed", e.getMessage()));
        }
    }

    public DatapackEditResult removeHead(CustomHead head) {
        if (head == null) {
            return DatapackEditResult.failure(message("no_head_selected"));
        }

        Optional<EditableFunction> target = editableFunctionFor(head);
        if (target.isEmpty()) {
            return DatapackEditResult.failure(message("head_file_missing"));
        }

        EditableFunction editableFunction = target.get();
        try {
            editFunction(editableFunction, content -> {
                Optional<String> updated = removeEntry(content, head.tradeIndex());
                if (updated.isEmpty()) {
                    throw new IOException("Trade #" + head.tradeIndex() + " was not found");
                }
                return updated.get();
            });
            refresh(lastScan.datapacksDirectory());
            return DatapackEditResult.success(message(ChatFormatting.RED, "removed_head", head.name()));
        } catch (IOException e) {
            return DatapackEditResult.failure(message("remove_failed", e.getMessage()));
        }
    }

    public DatapackScanResult lastScan() {
        return lastScan;
    }

    public void clear() {
        lastScan = DatapackScanResult.empty();
    }

    private Optional<EditableFunction> findPrimaryEditableFunction() {
        PackFormat packFormat = serverDataPackFormat();
        for (Path pack : lastScan.matchingPacks()) {
            Optional<String> functionPath = findFirstAddTradeFunction(pack, packFormat);
            if (functionPath.isPresent()) {
                return Optional.of(new EditableFunction(pack, functionPath.get()));
            }
        }

        return Optional.empty();
    }

    private Optional<EditableFunction> editableFunctionFor(CustomHead head) {
        if (lastScan.datapacksDirectory() == null || head.sourcePack().isBlank() || head.sourceFunction().isBlank()) {
            return Optional.empty();
        }

        return lastScan.matchingPacks().stream()
                .filter(path -> path.getFileName().toString().equals(head.sourcePack()))
                .findFirst()
                .map(path -> new EditableFunction(path, head.sourceFunction()));
    }

    private Optional<String> findFirstAddTradeFunction(Path pack, PackFormat packFormat) {
        if (Files.isDirectory(pack)) {
            return findAddTradeFunctions(pack, packFormat, pack.getFileName().toString(), new ArrayList<>()).stream().findFirst();
        }

        if (!isZip(pack)) {
            return Optional.empty();
        }

        URI uri = URI.create("jar:" + pack.toUri());

        try {
            return withZipRoot(uri, root -> findAddTradeFunctions(root, packFormat, pack.getFileName().toString(), new ArrayList<>()).stream().findFirst());
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static int compareCandidates(Path left, Path right) {
        return Comparator.comparingInt(WanderingTradesDatapackManager::candidateRank)
                .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
                .compare(left, right);
    }

    private static int candidateRank(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("wandering") && name.contains("trade")) return 0;
        if (name.contains("vanilla") && name.contains("tweak")) return 1;
        return 2;
    }

    private static void scanPack(
            Path pack,
            PackFormat packFormat,
            List<Path> matchingPacks,
            Map<String, CustomHead> heads,
            List<String> warnings
    ) {
        if (Files.isDirectory(pack)) {
            scanRoot(pack, pack, packFormat, matchingPacks, heads, warnings);
            return;
        }

        if (!isZip(pack)) {
            return;
        }

        URI uri = URI.create("jar:" + pack.toUri());

        try {
            withZipRoot(uri, root -> {
                scanRoot(pack, root, packFormat, matchingPacks, heads, warnings);
                return null;
            });
        } catch (IOException | RuntimeException e) {
            warnings.add("Failed to scan datapack " + pack.getFileName() + ": " + e.getMessage());
        }
    }

    private static void scanRoot(
            Path externalPack,
            Path root,
            PackFormat packFormat,
            List<Path> matchingPacks,
            Map<String, CustomHead> heads,
            List<String> warnings
    ) {
        boolean matchedPack = false;
        String packName = externalPack.getFileName().toString();

        for (String functionPath : findAddTradeFunctions(root, packFormat, packName, warnings)) {
            Path function = resolve(root, functionPath);
            if (!Files.isRegularFile(function)) {
                continue;
            }

            matchedPack = true;

            try {
                String content = Files.readString(function, StandardCharsets.UTF_8);
                List<ProvideTradeRange> providerRanges = provideTradeRanges(root, new EditableFunction(externalPack, functionPath));
                for (CustomHead head : HeadCommandParser.parseFunction(content, packName, functionPath)) {
                    if (isAvailableTradeIndex(head.tradeIndex(), providerRanges)) {
                        heads.putIfAbsent(head.dedupeKey(), head);
                    }
                }
            } catch (IOException e) {
                warnings.add("Failed to read " + functionPath + " from " + packName + ": " + e.getMessage());
            }
        }

        if (matchedPack) {
            matchingPacks.add(externalPack);
        }
    }

    private static List<String> findAddTradeFunctions(Path root, PackFormat packFormat, String packName, List<String> warnings) {
        Map<String, String> pathsByResource = new LinkedHashMap<>();
        List<String> activeOverlays = activeOverlayDirectories(root, packFormat, packName, warnings);

        for (int i = activeOverlays.size() - 1; i >= 0; i--) {
            addAddTradeFunctions(root, activeOverlays.get(i), pathsByResource);
        }

        addAddTradeFunctions(root, "", pathsByResource);

        return List.copyOf(pathsByResource.values());
    }

    private static void addAddTradeFunctions(Path root, String dataRoot, Map<String, String> pathsByResource) {
        for (String resourcePath : ADD_TRADE_RESOURCE_PATHS) {
            String functionPath = rootedDataPath(dataRoot, resourcePath);
            if (Files.isRegularFile(resolve(root, functionPath))) {
                pathsByResource.putIfAbsent(resourcePath, functionPath);
            }
        }

        Path searchRoot = resolve(root, rootedDataPath(dataRoot, "data/wandering_trades"));
        if (!Files.isDirectory(searchRoot)) {
            return;
        }

        try (var stream = Files.walk(searchRoot, 6)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> toRelativeString(root, path))
                    .filter(WanderingTradesDatapackManager::isAddTradeFunction)
                    .forEach(path -> pathsByResource.putIfAbsent(dataResourcePath(path), path));
        } catch (IOException ignored) {
        }

    }

    private static List<String> activeOverlayDirectories(Path root, PackFormat packFormat, String packName, List<String> warnings) {
        Path metadataFile = resolve(root, PACK_MCMETA);
        if (!Files.isRegularFile(metadataFile)) {
            return List.of();
        }

        try {
            JsonElement metadata = JsonParser.parseString(Files.readString(metadataFile, StandardCharsets.UTF_8));
            if (!metadata.isJsonObject()) {
                return List.of();
            }

            JsonObject metadataObject = metadata.getAsJsonObject();
            if (!metadataObject.has(OVERLAYS_KEY)) {
                return List.of();
            }

            return OverlayMetadataSection.codecForPackType(PackType.SERVER_DATA)
                    .parse(JsonOps.INSTANCE, metadataObject.get(OVERLAYS_KEY))
                    .resultOrPartial(message -> warnings.add("Failed to parse overlays in " + packName + ": " + message))
                    .map(section -> existingOverlayDirectories(root, packName, section.overlaysForVersion(packFormat), warnings))
                    .orElseGet(List::of);
        } catch (IOException | RuntimeException e) {
            warnings.add("Failed to read overlays in " + packName + ": " + e.getMessage());
            return List.of();
        }
    }

    private static List<String> existingOverlayDirectories(Path root, String packName, List<String> overlays, List<String> warnings) {
        List<String> active = new ArrayList<>();

        for (String overlay : overlays) {
            if (overlay == null || overlay.isBlank()) {
                continue;
            }

            if (Files.isDirectory(resolve(root, overlay))) {
                active.add(overlay);
            } else {
                warnings.add("Active overlay directory " + overlay + " declared by " + packName + " was not found");
            }
        }

        return active;
    }

    private static String rootedDataPath(String dataRoot, String dataPath) {
        if (dataRoot == null || dataRoot.isBlank()) {
            return dataPath;
        }

        return dataRoot.replace('\\', '/').replaceAll("/+$", "") + "/" + dataPath;
    }

    private static String dataResourcePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("data/")) {
            return normalized;
        }

        int dataStart = normalized.indexOf("/data/");
        return dataStart >= 0 ? normalized.substring(dataStart + 1) : normalized;
    }

    private static boolean isAddTradeFunction(String path) {
        return path.endsWith("data/wandering_trades/function/add_trade.mcfunction")
                || path.endsWith("data/wandering_trades/functions/add_trade.mcfunction");
    }

    private static String toRelativeString(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Path resolve(Path root, String slashSeparatedPath) {
        Path path = root;
        for (String segment : slashSeparatedPath.split("/")) {
            if (!segment.isEmpty()) {
                path = path.resolve(segment);
            }
        }
        return path;
    }

    private static boolean editPack(Path pack, PackEdit edit) throws IOException {
        if (Files.isDirectory(pack)) {
            return edit.apply(pack);
        }

        if (!isZip(pack)) {
            throw new IOException("Not a folder or zip datapack: " + pack.getFileName());
        }

        Path tempZip = null;
        Path restoreZip = null;

        try {
            tempZip = Files.createTempFile(pack.getParent(), TEMP_PACK_PREFIX, TEMP_PACK_SUFFIX);
            Files.copy(pack, tempZip, StandardCopyOption.REPLACE_EXISTING);

            URI uri = URI.create("jar:" + tempZip.toUri());
            boolean changed;
            try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of())) {
                changed = edit.apply(fileSystem.getPath("/"));
            }

            if (!changed) {
                return false;
            }

            restoreZip = Files.createTempFile(TEMP_PACK_PREFIX, TEMP_PACK_SUFFIX);
            Files.copy(pack, restoreZip, StandardCopyOption.REPLACE_EXISTING);

            try {
                replaceZip(pack, tempZip);
                return true;
            } catch (IOException e) {
                try {
                    replaceFileContents(restoreZip, pack);
                } catch (IOException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
                throw e;
            }
        } finally {
            if (tempZip != null) {
                Files.deleteIfExists(tempZip);
            }
            if (restoreZip != null) {
                Files.deleteIfExists(restoreZip);
            }
        }
    }

    private static boolean editFunction(EditableFunction target, FunctionContentEdit edit) throws IOException {
        return editPack(target.pack(), root -> {
            Path function = resolve(root, target.functionPath());
            String content = Files.readString(function, StandardCharsets.UTF_8);
            String updated = edit.apply(content);
            if (content.equals(updated)) {
                return false;
            }

            writeString(function, updated);
            updateTradeRanges(root, target, updated);
            return true;
        });
    }

    private static <T> T withZipRoot(URI uri, ZipRootReader<T> reader) throws IOException {
        FileSystem fileSystem = null;
        boolean closeFileSystem = false;

        try {
            try {
                fileSystem = FileSystems.newFileSystem(uri, Map.of());
                closeFileSystem = true;
            } catch (FileSystemAlreadyExistsException ignored) {
                fileSystem = FileSystems.getFileSystem(uri);
            }

            return reader.read(fileSystem.getPath("/"));
        } finally {
            if (closeFileSystem && fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void replaceZip(Path target, Path replacement) throws IOException {
        try {
            replaceFileContents(replacement, target);
        } catch (IOException overwriteFailure) {
            try {
                Files.move(replacement, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveFailure) {
                try {
                    Files.move(replacement, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveFailure) {
                    overwriteFailure.addSuppressed(atomicMoveFailure);
                    overwriteFailure.addSuppressed(moveFailure);
                    throw overwriteFailure;
                }
            }
        }
    }

    private static void replaceFileContents(Path source, Path target) throws IOException {
        try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long size = input.size();
            long position = 0;
            while (position < size) {
                long transferred = input.transferTo(position, size - position, output);
                if (transferred <= 0) {
                    throw new IOException("Failed to copy replacement datapack zip contents");
                }
                position += transferred;
            }
            output.truncate(size);
        }
    }

    private static void writeString(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static int nextTradeIndex(String content) {
        return maxTradeIndex(content, 0) + 1;
    }

    private static String appendTradeEntry(String content, String entry) {
        String lineSeparator = lineSeparator(content);
        String stripped = content.stripTrailing();
        if (stripped.isBlank()) {
            return entry + lineSeparator;
        }
        return stripped + lineSeparator + lineSeparator + entry + lineSeparator;
    }

    private static String lineSeparator(String content) {
        return content.contains("\r\n") ? "\r\n" : System.lineSeparator();
    }

    private static int maxTradeIndex(String content, int fallback) {
        int max = fallback;
        Matcher matcher = TRADE_INDEX_PATTERN.matcher(content);
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
    }

    private static String generateTradeEntry(int tradeIndex, String name, String textureValue) {
        return "execute if score @s wt_tradeIndex matches " + tradeIndex + " run data modify entity @s Offers.Recipes prepend value "
                + "{rewardExp:0b,maxUses:1,buy:{id:\"minecraft:emerald\"},"
                + "sell:{id:\"minecraft:player_head\",count:5,components:{\"minecraft:item_name\":" + itemNameValue(name) + ","
                + "\"minecraft:rarity\":\"uncommon\",\"minecraft:profile\":{properties:[{name:\"textures\",value:\""
                + textureValue + "\"}]}}}}";
    }

    private static String updateEntry(String entry, int tradeIndex, String name, String textureValue) {
        Matcher nameMatcher = ITEM_NAME_ASSIGNMENT_PATTERN.matcher(entry);
        String updated = nameMatcher.find()
                ? nameMatcher.replaceFirst(Matcher.quoteReplacement(nameMatcher.group(1) + itemNameValue(name)))
                : generateTradeEntry(tradeIndex, name, textureValue);

        Matcher textureMatcher = TEXTURE_ASSIGNMENT_PATTERN.matcher(updated);
        return textureMatcher.find()
                ? textureMatcher.replaceFirst(Matcher.quoteReplacement(textureMatcher.group(1) + textureValue + textureMatcher.group(3)))
                : generateTradeEntry(tradeIndex, name, textureValue);
    }

    private static Optional<String> replaceEntry(String content, int tradeIndex, EntryEditor editor) throws IOException {
        int[] span = findEntrySpan(content, tradeIndex);
        if (span == null) {
            return Optional.empty();
        }

        String entry = content.substring(span[0], span[1]);
        String replacement = editor.edit(entry);
        return Optional.of(content.substring(0, span[0]) + replacement + content.substring(span[1]));
    }

    private static Optional<String> removeEntry(String content, int tradeIndex) {
        int[] span = findEntrySpan(content, tradeIndex);
        if (span == null) {
            return Optional.empty();
        }

        int start = span[0];
        int end = span[1];
        while (end < content.length() && (content.charAt(end) == '\r' || content.charAt(end) == '\n')) {
            end++;
        }
        return Optional.of(content.substring(0, start) + content.substring(end));
    }

    private static int[] findEntrySpan(String content, int tradeIndex) {
        Matcher matcher = TRADE_INDEX_PATTERN.matcher(content);
        int start = -1;

        while (matcher.find()) {
            if (start >= 0) {
                return new int[]{start, matcher.start()};
            }
            if (Integer.parseInt(matcher.group(1)) == tradeIndex) {
                start = matcher.start();
            }
        }

        return start >= 0 ? new int[]{start, content.length()} : null;
    }

    private static void updateTradeRanges(Path root, EditableFunction target, String addTradeContent) throws IOException {
        List<Integer> tradeIndexes = tradeIndexes(addTradeContent);
        if (tradeIndexes.isEmpty()) {
            return;
        }

        List<ProvideTradeRange> providerRanges = provideTradeRanges(root, target);
        for (int i = 0; i < providerRanges.size(); i++) {
            ProvideTradeRange providerRange = providerRanges.get(i);
            int upperExclusive = i + 1 < providerRanges.size()
                    ? providerRanges.get(i + 1).lower()
                    : Integer.MAX_VALUE;

            Optional<TradeIndexRange> tradeIndexRange = tradeIndexRange(tradeIndexes, providerRange.lower(), upperExclusive);
            if (tradeIndexRange.isPresent()) {
                updateProvideTradeRange(providerRange, tradeIndexRange.get());
            }
        }
    }

    private static List<Integer> tradeIndexes(String content) {
        List<Integer> indexes = new ArrayList<>();
        Matcher matcher = TRADE_INDEX_PATTERN.matcher(content);
        while (matcher.find()) {
            indexes.add(Integer.parseInt(matcher.group(1)));
        }
        indexes.sort(Integer::compareTo);
        return indexes;
    }

    private static List<ProvideTradeRange> provideTradeRanges(Path root, EditableFunction target) throws IOException {
        List<ProvideTradeRange> ranges = new ArrayList<>();
        List<String> activeOverlays = activeOverlayDirectories(root, serverDataPackFormat(), target.pack().getFileName().toString(), new ArrayList<>());
        for (String functionName : PROVIDE_TRADE_FUNCTION_NAMES) {
            findProvideTradeRange(root, target.functionPath(), functionName, activeOverlays)
                    .ifPresent(ranges::add);
        }

        ranges.sort(Comparator.comparingInt(ProvideTradeRange::lower));
        return ranges;
    }

    private static boolean isAvailableTradeIndex(int tradeIndex, List<ProvideTradeRange> providerRanges) {
        for (ProvideTradeRange providerRange : providerRanges) {
            if (tradeIndex >= providerRange.lower() && tradeIndex <= providerRange.upper()) {
                return true;
            }
        }

        return false;
    }

    private static Optional<ProvideTradeRange> findProvideTradeRange(Path root, String addTradeFunctionPath, String functionName, List<String> activeOverlays) throws IOException {
        for (String providePath : provideTradeFunctionCandidates(root, addTradeFunctionPath, functionName, activeOverlays)) {
            Path provideFile = resolve(root, providePath);
            if (!Files.isRegularFile(provideFile)) {
                continue;
            }

            String content = Files.readString(provideFile, StandardCharsets.UTF_8);
            Matcher matcher = RANDOM_RANGE_PATTERN.matcher(content);
            if (matcher.find()) {
                return Optional.of(new ProvideTradeRange(
                        provideFile,
                        content,
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                ));
            }
        }

        return Optional.empty();
    }

    private static List<String> provideTradeFunctionCandidates(Path root, String addTradeFunctionPath, String functionName, List<String> activeOverlays) throws IOException {
        Set<String> candidates = new LinkedHashSet<>();

        for (int i = activeOverlays.size() - 1; i >= 0; i--) {
            addStandardProvideTradeCandidates(candidates, activeOverlays.get(i), functionName);
        }
        addStandardProvideTradeCandidates(candidates, "", functionName);

        String siblingPath = addTradeFunctionPath.replace("add_trade.mcfunction", functionName);
        candidates.add(siblingPath);
        candidates.add(dataResourcePath(siblingPath));
        addDiscoveredProvideTradeCandidates(root, functionName, candidates);

        return List.copyOf(candidates);
    }

    private static void addStandardProvideTradeCandidates(Set<String> candidates, String dataRoot, String functionName) {
        candidates.add(rootedDataPath(dataRoot, "data/wandering_trades/function/" + functionName));
        candidates.add(rootedDataPath(dataRoot, "data/wandering_trades/functions/" + functionName));
    }

    private static void addDiscoveredProvideTradeCandidates(Path root, String functionName, Set<String> candidates) {
        try (var stream = Files.walk(root, 10)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> toRelativeString(root, path))
                    .filter(path -> isWanderingTradesFunction(path, functionName))
                    .forEach(candidates::add);
        } catch (IOException ignored) {
        }
    }

    private static boolean isWanderingTradesFunction(String path, String functionName) {
        String normalized = path.replace('\\', '/');
        return normalized.endsWith("/" + functionName)
                && (normalized.startsWith("data/wandering_trades/") || normalized.contains("/data/wandering_trades/"));
    }

    private static Optional<TradeIndexRange> tradeIndexRange(List<Integer> sortedIndexes, int lowerInclusive, int upperExclusive) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int index : sortedIndexes) {
            if (index < lowerInclusive) {
                continue;
            }
            if (index >= upperExclusive) {
                break;
            }

            min = Math.min(min, index);
            max = Math.max(max, index);
        }

        return min == Integer.MAX_VALUE ? Optional.empty() : Optional.of(new TradeIndexRange(min, max));
    }

    private static void updateProvideTradeRange(ProvideTradeRange providerRange, TradeIndexRange tradeIndexRange) throws IOException {
        if (providerRange.lower() == tradeIndexRange.min() && providerRange.upper() == tradeIndexRange.max()) {
            return;
        }

        Matcher matcher = RANDOM_RANGE_PATTERN.matcher(providerRange.content());
        if (matcher.find()) {
            String updated = matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + tradeIndexRange.min() + ".." + tradeIndexRange.max()));
            writeString(providerRange.file(), updated);
        }
    }

    private static String cleanName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String itemNameValue(String value) {
        return "'" + escapeCommandText(value) + "'";
    }

    private static String escapeCommandText(String value) {
        return cleanName(value)
                .replace("\\", "")
                .replace("\"", "")
                .replace("'", "");
    }

    private static boolean isZip(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip");
    }

    private static void cleanStaleTempPacks(Path datapacksDirectory, List<String> warnings) {
        try (var stream = Files.list(datapacksDirectory)) {
            for (Path pack : stream.filter(WanderingTradesDatapackManager::isTemporaryPack).toList()) {
                try {
                    Files.deleteIfExists(pack);
                } catch (IOException e) {
                    warnings.add("Failed to remove stale Wandering Trades Manager temp pack " + pack.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            warnings.add("Failed to clean stale Wandering Trades Manager temp packs in " + datapacksDirectory + ": " + e.getMessage());
        }
    }

    private static boolean isTemporaryPack(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith(TEMP_PACK_PREFIX) && (name.endsWith(".zip") || name.endsWith(TEMP_PACK_SUFFIX));
    }

    private static Component message(String key, Object... args) {
        return Component.translatable("message.wanderingtradesmanager." + key, args);
    }

    private static Component message(ChatFormatting color, String key, Object... args) {
        return message(key, args).copy().withStyle(color);
    }

    private record EditableFunction(Path pack, String functionPath) {
    }

    private record ProvideTradeRange(Path file, String content, int lower, int upper) {
    }

    private record TradeIndexRange(int min, int max) {
    }

    @FunctionalInterface
    private interface PackEdit {
        boolean apply(Path root) throws IOException;
    }

    @FunctionalInterface
    private interface FunctionContentEdit {
        String apply(String content) throws IOException;
    }

    @FunctionalInterface
    private interface ZipRootReader<T> {
        T read(Path root) throws IOException;
    }

    @FunctionalInterface
    private interface EntryEditor {
        String edit(String entry) throws IOException;
    }
}
