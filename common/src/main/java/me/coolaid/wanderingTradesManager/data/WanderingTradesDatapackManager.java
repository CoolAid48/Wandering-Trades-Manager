package me.coolaid.wanderingTradesManager.data;

import me.coolaid.wanderingTradesManager.parser.HeadCommandParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final List<String> ADD_TRADE_FUNCTION_PATHS = List.of(
            "overlay_71/data/wandering_trades/function/add_trade.mcfunction",
            "overlay_71/data/wandering_trades/functions/add_trade.mcfunction",
            "data/wandering_trades/function/add_trade.mcfunction",
            "data/wandering_trades/functions/add_trade.mcfunction"
    );
    private static final Pattern TRADE_INDEX_PATTERN = Pattern.compile("execute\\s+if\\s+score\\s+@s\\s+wt_tradeIndex\\s+matches\\s+(\\d+)");
    private static final Pattern RANDOM_RANGE_PATTERN = Pattern.compile("(execute\\s+store\\s+result\\s+score\\s+@s\\s+wt_tradeIndex\\s+run\\s+random\\s+value\\s+)(\\d+)\\.\\.(\\d+)");
    private static final Pattern ITEM_NAME_ASSIGNMENT_PATTERN = Pattern.compile("(\"minecraft:(?:item_name|custom_name)\"\\s*:\\s*)(?:'\"[^\"]*\"'|\"[^\"]*\")");
    private static final Pattern TEXTURE_ASSIGNMENT_PATTERN = Pattern.compile("(value\\s*:\\s*\")([A-Za-z0-9+/=]+)(\")");
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String TEMP_PACK_PREFIX = "wtm_edit_";
    private static final String TEMP_PACK_SUFFIX = ".wtm-tmp";

    private DatapackScanResult lastScan = DatapackScanResult.empty();

    public DatapackScanResult refresh(MinecraftServer server) {
        return refresh(server.getWorldPath(LevelResource.DATAPACK_DIR));
    }

    public DatapackScanResult refresh(Path datapacksDirectory) {
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
                scanPack(pack, matchingPacks, heads, warnings);
            }
        } catch (IOException e) {
            warnings.add("Failed to list datapacks in " + datapacksDirectory + ": " + e.getMessage());
        }

        lastScan = new DatapackScanResult(datapacksDirectory, matchingPacks, heads.values().stream().toList(), warnings);
        return lastScan;
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

        String duplicateName = name;
        if (lastScan.heads().stream().anyMatch(head -> head.name().equalsIgnoreCase(duplicateName))) {
            return DatapackEditResult.failure(message("duplicate_name", name));
        }
        if (lastScan.heads().stream().anyMatch(head -> head.textureValue().equals(texture))) {
            return DatapackEditResult.failure(message("duplicate_texture"));
        }

        Optional<EditableFunction> target = findPrimaryEditableFunction();
        if (target.isEmpty()) {
            return DatapackEditResult.failure(message("no_editable_function"));
        }

        String finalName = name;
        String finalTexture = texture;
        try {
            editPack(target.get().pack(), root -> {
                Path function = resolve(root, target.get().functionPath());
                String content = Files.readString(function, StandardCharsets.UTF_8);
                int nextIndex = nextTradeIndex(content);
                String updated = content.stripTrailing() + System.lineSeparator() + generateTradeEntry(nextIndex, finalName, finalTexture) + System.lineSeparator();
                writeStringWithBackup(function, updated);
                updateTradeRange(root, target.get().functionPath(), updated);
            });
            refresh(lastScan.datapacksDirectory());
            return DatapackEditResult.success(message("added_head", finalName));
        } catch (IOException e) {
            return DatapackEditResult.failure(message("add_failed", e.getMessage()));
        }
    }

    public DatapackEditResult updateHead(CustomHead original, String requestedName, String textureValue) {
        if (original == null) {
            return DatapackEditResult.failure(message("no_head_selected"));
        }

        Optional<EditableFunction> target = editableFunctionFor(original);
        if (target.isEmpty()) {
            return DatapackEditResult.failure(message("head_file_missing"));
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

        try {
            editPack(target.get().pack(), root -> {
                Path function = resolve(root, target.get().functionPath());
                String content = Files.readString(function, StandardCharsets.UTF_8);
                String updated = replaceEntry(content, original.tradeIndex(), entry -> updateEntry(entry, original.tradeIndex(), name, texture));
                if (content.equals(updated)) {
                    throw new IOException("Trade #" + original.tradeIndex() + " was not found");
                }
                writeStringWithBackup(function, updated);
                updateTradeRange(root, target.get().functionPath(), updated);
            });
            refresh(lastScan.datapacksDirectory());
            return DatapackEditResult.success(message("updated_head", name));
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

        try {
            editPack(target.get().pack(), root -> {
                Path function = resolve(root, target.get().functionPath());
                String content = Files.readString(function, StandardCharsets.UTF_8);
                String updated = removeEntry(content, head.tradeIndex());
                if (content.equals(updated)) {
                    throw new IOException("Trade #" + head.tradeIndex() + " was not found");
                }
                writeStringWithBackup(function, updated);
                updateTradeRange(root, target.get().functionPath(), updated);
            });
            refresh(lastScan.datapacksDirectory());
            return DatapackEditResult.success(message("removed_head", head.name()));
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
        for (Path pack : lastScan.matchingPacks()) {
            Optional<String> functionPath = findFirstAddTradeFunction(pack);
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

    private Optional<String> findFirstAddTradeFunction(Path pack) {
        if (Files.isDirectory(pack)) {
            return findAddTradeFunctions(pack).stream().findFirst();
        }

        if (!isZip(pack)) {
            return Optional.empty();
        }

        URI uri = URI.create("jar:" + pack.toUri());
        FileSystem fileSystem = null;
        boolean closeFileSystem = false;

        try {
            try {
                fileSystem = FileSystems.newFileSystem(uri, Map.of());
                closeFileSystem = true;
            } catch (FileSystemAlreadyExistsException ignored) {
                fileSystem = FileSystems.getFileSystem(uri);
            }

            return findAddTradeFunctions(fileSystem.getPath("/")).stream().findFirst();
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        } finally {
            if (closeFileSystem && fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException ignored) {
                }
            }
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
            List<Path> matchingPacks,
            Map<String, CustomHead> heads,
            List<String> warnings
    ) {
        if (Files.isDirectory(pack)) {
            scanRoot(pack, pack, matchingPacks, heads, warnings);
            return;
        }

        if (!isZip(pack)) {
            return;
        }

        URI uri = URI.create("jar:" + pack.toUri());
        FileSystem fileSystem = null;
        boolean closeFileSystem = false;

        try {
            try {
                fileSystem = FileSystems.newFileSystem(uri, Map.of());
                closeFileSystem = true;
            } catch (FileSystemAlreadyExistsException ignored) {
                fileSystem = FileSystems.getFileSystem(uri);
            }

            scanRoot(pack, fileSystem.getPath("/"), matchingPacks, heads, warnings);
        } catch (IOException | RuntimeException e) {
            warnings.add("Failed to scan datapack " + pack.getFileName() + ": " + e.getMessage());
        } finally {
            if (closeFileSystem && fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void scanRoot(
            Path externalPack,
            Path root,
            List<Path> matchingPacks,
            Map<String, CustomHead> heads,
            List<String> warnings
    ) {
        boolean matchedPack = false;
        String packName = externalPack.getFileName().toString();

        for (String functionPath : findAddTradeFunctions(root)) {
            Path function = resolve(root, functionPath);
            if (!Files.isRegularFile(function)) {
                continue;
            }

            matchedPack = true;

            try {
                String content = Files.readString(function, StandardCharsets.UTF_8);
                for (CustomHead head : HeadCommandParser.parseFunction(content, packName, functionPath)) {
                    heads.putIfAbsent(head.dedupeKey(), head);
                }
            } catch (IOException e) {
                warnings.add("Failed to read " + functionPath + " from " + packName + ": " + e.getMessage());
            }
        }

        if (matchedPack) {
            matchingPacks.add(externalPack);
        }
    }

    private static List<String> findAddTradeFunctions(Path root) {
        Set<String> paths = new LinkedHashSet<>();

        for (String functionPath : ADD_TRADE_FUNCTION_PATHS) {
            if (Files.isRegularFile(resolve(root, functionPath))) {
                paths.add(functionPath);
            }
        }

        try (var stream = Files.walk(root, 8)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> toRelativeString(root, path))
                    .filter(WanderingTradesDatapackManager::isAddTradeFunction)
                    .forEach(paths::add);
        } catch (IOException ignored) {
        }

        return List.copyOf(paths);
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

    private static void editPack(Path pack, PackEdit edit) throws IOException {
        if (Files.isDirectory(pack)) {
            edit.apply(pack);
            return;
        }

        if (!isZip(pack)) {
            throw new IOException("Not a folder or zip datapack: " + pack.getFileName());
        }

        Path backup = backupPath(pack);
        Files.copy(pack, backup, StandardCopyOption.COPY_ATTRIBUTES);

        Path tempZip = Files.createTempFile(pack.getParent(), TEMP_PACK_PREFIX, TEMP_PACK_SUFFIX);

        try {
            Files.copy(pack, tempZip, StandardCopyOption.REPLACE_EXISTING);

            URI uri = URI.create("jar:" + tempZip.toUri());
            try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of())) {
                edit.apply(fileSystem.getPath("/"));
            }

            try {
                replaceZip(pack, tempZip);
            } catch (IOException e) {
                try {
                    replaceFileContents(backup, pack);
                } catch (IOException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
                throw e;
            }
        } finally {
            Files.deleteIfExists(tempZip);
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

    private static Path backupPath(Path path) {
        String stamp = LocalDateTime.now().format(BACKUP_STAMP);
        Path backup = path.resolveSibling(path.getFileName() + ".wtm-backup-" + stamp);
        int attempt = 1;
        while (Files.exists(backup)) {
            backup = path.resolveSibling(path.getFileName() + ".wtm-backup-" + stamp + "-" + attempt);
            attempt++;
        }
        return backup;
    }

    private static void writeStringWithBackup(Path file, String content) throws IOException {
        if (Files.exists(file) && file.getFileSystem().equals(FileSystems.getDefault())) {
            Files.copy(file, backupPath(file), StandardCopyOption.COPY_ATTRIBUTES);
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static int nextTradeIndex(String content) {
        return parseTradeIndices(content).stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    private static List<Integer> parseTradeIndices(String content) {
        List<Integer> indices = new ArrayList<>();
        Matcher matcher = TRADE_INDEX_PATTERN.matcher(content);
        while (matcher.find()) {
            indices.add(Integer.parseInt(matcher.group(1)));
        }
        return indices;
    }

    private static String generateTradeEntry(int tradeIndex, String name, String textureValue) {
        return "execute if score @s wt_tradeIndex matches " + tradeIndex + " run data modify entity @s Offers.Recipes prepend value "
                + "{rewardExp:0b,maxUses:3,buy:{id:\"minecraft:emerald\"},"
                + "sell:{id:\"minecraft:player_head\",count:1,components:{\"minecraft:item_name\":'\"" + escapeCommandText(name) + "\"',"
                + "\"minecraft:rarity\":\"uncommon\",\"minecraft:profile\":{properties:[{name:\"textures\",value:\""
                + textureValue + "\"}]}}}}";
    }

    private static String updateEntry(String entry, int tradeIndex, String name, String textureValue) {
        Matcher nameMatcher = ITEM_NAME_ASSIGNMENT_PATTERN.matcher(entry);
        String updated = nameMatcher.find()
                ? nameMatcher.replaceFirst(Matcher.quoteReplacement(nameMatcher.group(1) + "'\"" + escapeCommandText(name) + "\"'"))
                : generateTradeEntry(tradeIndex, name, textureValue);

        Matcher textureMatcher = TEXTURE_ASSIGNMENT_PATTERN.matcher(updated);
        return textureMatcher.find()
                ? textureMatcher.replaceFirst(Matcher.quoteReplacement(textureMatcher.group(1) + textureValue + textureMatcher.group(3)))
                : generateTradeEntry(tradeIndex, name, textureValue);
    }

    private static String replaceEntry(String content, int tradeIndex, EntryEditor editor) throws IOException {
        int[] span = findEntrySpan(content, tradeIndex);
        if (span == null) {
            return content;
        }

        String entry = content.substring(span[0], span[1]);
        String replacement = editor.edit(entry);
        return content.substring(0, span[0]) + replacement + content.substring(span[1]);
    }

    private static String removeEntry(String content, int tradeIndex) {
        int[] span = findEntrySpan(content, tradeIndex);
        if (span == null) {
            return content;
        }

        int start = span[0];
        int end = span[1];
        while (end < content.length() && (content.charAt(end) == '\r' || content.charAt(end) == '\n')) {
            end++;
        }
        return content.substring(0, start) + content.substring(end);
    }

    private static int[] findEntrySpan(String content, int tradeIndex) {
        Matcher matcher = TRADE_INDEX_PATTERN.matcher(content);
        List<Integer> starts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        while (matcher.find()) {
            starts.add(matcher.start());
            indices.add(Integer.parseInt(matcher.group(1)));
        }

        for (int i = 0; i < indices.size(); i++) {
            if (indices.get(i) == tradeIndex) {
                int start = starts.get(i);
                int end = i + 1 < starts.size() ? starts.get(i + 1) : content.length();
                return new int[]{start, end};
            }
        }

        return null;
    }

    private static void updateTradeRange(Path root, String addTradeFunctionPath, String addTradeContent) throws IOException {
        String providePath = addTradeFunctionPath.replace("add_trade.mcfunction", "provide_block_trades.mcfunction");
        Path provideFile = resolve(root, providePath);
        if (!Files.isRegularFile(provideFile) && providePath.startsWith("overlay_71/")) {
            providePath = providePath.substring("overlay_71/".length());
            provideFile = resolve(root, providePath);
        }
        if (!Files.isRegularFile(provideFile)) {
            return;
        }

        String content = Files.readString(provideFile, StandardCharsets.UTF_8);
        Matcher matcher = RANDOM_RANGE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return;
        }

        int lower = Integer.parseInt(matcher.group(2));
        int upper = Integer.parseInt(matcher.group(3));
        int maxIndex = parseTradeIndices(addTradeContent).stream().mapToInt(Integer::intValue).max().orElse(upper);
        int newUpper = Math.max(lower, maxIndex);
        if (newUpper == upper) {
            return;
        }

        String updated = matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + lower + ".." + newUpper));
        writeStringWithBackup(provideFile, updated);
    }

    private static String cleanName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String escapeCommandText(String value) {
        return cleanName(value)
                .replace("\\", "")
                .replace("\"", "\\\"")
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

    private record EditableFunction(Path pack, String functionPath) {
    }

    @FunctionalInterface
    private interface PackEdit {
        void apply(Path root) throws IOException;
    }

    @FunctionalInterface
    private interface EntryEditor {
        String edit(String entry) throws IOException;
    }
}
