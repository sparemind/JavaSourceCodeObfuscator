package com.jakechiang.obfuscate;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Merger.obfuscationMerge("./src/main/java/com", "Merged.java");
    }
}
