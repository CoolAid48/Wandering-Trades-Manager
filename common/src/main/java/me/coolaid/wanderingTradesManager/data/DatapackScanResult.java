package me.coolaid.wanderingTradesManager.data;

import java.nio.file.Path;
import java.util.List;

public record DatapackScanResult(
        Path datapacksDirectory,
        List<Path> matchingPacks,
        List<CustomHead> heads,
        List<String> warnings
) {
    public DatapackScanResult {
        matchingPacks = List.copyOf(matchingPacks);
        heads = List.copyOf(heads);
        warnings = List.copyOf(warnings);
    }

    public static DatapackScanResult empty() {
        return new DatapackScanResult(null, List.of(), List.of(), List.of());
    }

    public boolean hasMatchingPacks() {
        return !matchingPacks.isEmpty();
    }
}
