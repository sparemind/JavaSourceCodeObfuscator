package com.jakechiang.obfuscate;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Required args: <source_root> <output_file>");
            System.exit(1);
        }
        String sourceRoot = args[0];
        String outputFile = args[1];
        Merger.obfuscationMerge(sourceRoot, outputFile);
    }
}
