package com.jakechiang.obfuscate;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.refactoring.CtRenameGenericVariableRefactoring;
import spoon.refactoring.CtRenameLocalVariableRefactoring;
import spoon.refactoring.Refactoring;
import spoon.refactoring.RefactoringException;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.declaration.CtParameterImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Stack;

public class Merger {
    private static final String TEMP_OUTPUT = "/tmp/obfuscation";
    private static final int NUM_RENAMES = 10000;
    // Holds
    private static final int[] randomIndices = new int[NUM_RENAMES];
    private static int renameCounter = 0;

    static {
        for (int i = 0; i < randomIndices.length; i++) {
            randomIndices[i] = i;
        }
        // Fisher-Yates shuffle
        Random rand = new Random();
        for (int i = randomIndices.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = randomIndices[j];
            randomIndices[j] = randomIndices[i];
            randomIndices[i] = tmp;
        }
    }

    /**
     * Returns unique obfuscated name corresponding to a given index. If the
     * index is < than {@link #randomIndices} it will be a random
     * correspondence, otherwise it will be a name based on converting the
     * given index to an alphabetic radix.
     *
     * @param index The index of the obfuscated name to get.
     * @return An obfuscated name that's an alphabetic permutation.
     */
    private static String obfuscatedName(int index) {
        char[] str = Integer.toString(index, 26).toCharArray();
        for (int i = 0; i < str.length; i++) {
            str[i] += str[i] > '9' ? 10 : 49;
        }
        if (str.length > 1) {
            str[0]--;
        }
        renameCounter++;
        return "_" + new String(str);
    }

    /**
     * Returns a random obfuscated name that has not been returned by this
     * function or by {@link #obfuscatedName(int)} if this function has been
     * called fewer than {@link #NUM_RENAMES} times, otherwise returns the next
     * available obfuscated name.
     *
     * @return A random obfuscated name that's an alphabetic permutation.
     */
    private static String obfuscatedName() {
        if (renameCounter < NUM_RENAMES) {
            return obfuscatedName(randomIndices[renameCounter++]);
        } else {
            return obfuscatedName(renameCounter++);
        }
    }

    /**
     * Renames all local variables in a given list to random obfuscated names.
     *
     * @param variables The variables whose names to obfuscate.
     */
    private static void renameLocalVariables(List<CtLocalVariable> variables) {
        CtRenameLocalVariableRefactoring refactor = new CtRenameLocalVariableRefactoring();
        for (CtLocalVariable variable : variables) {
            refactor.setTarget(variable);
            refactor.setNewName(obfuscatedName());
            try {
                refactor.refactor();
            } catch (RefactoringException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Renames all parameters in a given list to random obfuscated names.
     *
     * @param parameters The parameters whose names to obfuscate.
     */
    private static void renameParameters(List<CtParameterImpl> parameters) {
        CtRenameGenericVariableRefactoring refactor = new CtRenameGenericVariableRefactoring();
        for (CtParameterImpl parameter : parameters) {
            refactor.setTarget(parameter);
            refactor.setNewName(obfuscatedName());
            try {
                refactor.refactor();
            } catch (RefactoringException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Renames all fields in a given list to random obfuscated names.
     *
     * @param fields The fields whose names to obfuscate.
     */
    private static void renameFields(List<CtField> fields) {
        CtRenameGenericVariableRefactoring refactor = new CtRenameGenericVariableRefactoring();
        for (CtField field : fields) {
            if (field.isFinal() && !field.isStatic()) {
                field.removeModifier(ModifierKind.FINAL);
            }
            refactor.setTarget(field);
            refactor.setNewName(obfuscatedName());
            try {
                refactor.refactor();
            } catch (RefactoringException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Renames all methods ina given list to random obfuscated names. Functions
     * named "main" will not be obfuscated.
     *
     * @param methods The methods whose names to obfuscate.
     */
    private static void renameMethods(List<CtMethod> methods) {
        // Rename top level methods
        Map<CtMethod, Collection> methodTopDefinitions = new HashMap<>();
        for (CtMethod method : methods) {
            if (method.getSimpleName().equals("main")) {
                continue;
            }
            Collection topDefinitions = method.getTopDefinitions();
            methodTopDefinitions.put(method, topDefinitions);

            if (topDefinitions.isEmpty()) {
                Refactoring.changeMethodName(method, obfuscatedName());
            }
        }
        // Rename overriding methods to match the name of their parent
        for (CtMethod method : methods) {
            Collection topDefinitions = methodTopDefinitions.get(method);
            if (topDefinitions != null && !topDefinitions.isEmpty()) {
                Refactoring.changeMethodName(method, ((CtMethod) topDefinitions.iterator().next()).getSimpleName());
            }
        }
    }

    /**
     * Returns a mapping of string literal values enclosed in double quotes (")
     * to a string of obfuscated code that produces the same string literals.
     * The strings are formatted such that
     *
     * @param literals The literals to make obfuscations for. Any literals that
     *                 are not string literals will be ignored.
     * @return A mapping of string literal values to string of obfuscated code
     * that produces the same string literals.
     */
    private static Map<String, String> getStringLiteralObfuscations(List<CtLiteral> literals) {
        Map<String, String> obfuscations = new HashMap<>();
        for (CtLiteral literal : literals) {
            Object value = literal.getValue();
            if (value == null) {
                continue;
            }
            if (value.getClass().equals(String.class)) {
                StringBuilder sb = new StringBuilder();
                String stringValue = value.toString();
                if (stringValue.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < stringValue.length() - 1; i++) {
                    sb.append('"').append(stringValue.charAt(i)).append("\"+");
                }
                sb.append('"').append(stringValue.charAt(stringValue.length() - 1)).append('"');
                obfuscations.put('"' + stringValue + '"', sb.toString());
            }
        }
        return obfuscations;
    }

    /**
     * Merges the .java files found in a give source root directory into a
     * single file and obfuscates the code by randomly obfuscating the names of
     * all classes, interfaces, methods, fields, and variables.
     *
     * @param sourceRoot     The path of the source root directory to obfuscate.
     * @param mergedFilePath The path to a file to store the obfuscated and
     *                       merged source code in.
     * @throws FileNotFoundException If a .java file in the source root or a
     *                               subdirectory cannot be found while
     *                               attempting to read from it or if the
     *                               merged results cannot be written to a file
     *                               at the given output filepath.
     */
    public static void obfuscationMerge(String sourceRoot, String mergedFilePath) throws FileNotFoundException {
        // Create empty directory for temporary files
        String tmpOutputPath = TEMP_OUTPUT;
        File tmpOutput = new File(tmpOutputPath);
        for (int i = 0; tmpOutput.exists(); i++) {
            tmpOutputPath = TEMP_OUTPUT + i;
            tmpOutput = new File(tmpOutputPath);
        }
        tmpOutput.mkdir();

        Launcher launcher = new Launcher();
        Environment env = launcher.getEnvironment();
        CtPackage rootPackage = launcher.getFactory().Package().getRootPackage();
        env.setAutoImports(false);
        env.setCommentEnabled(false);
        launcher.addInputResource(sourceRoot);
        launcher.setSourceOutputDirectory(tmpOutputPath);
        launcher.buildModel();

        List<CtLiteral> literals = rootPackage.getElements(new TypeFilter<>(CtLiteral.class));
        System.out.println("# Literals: " + literals.size());
        Map<String, String> stringLiteralObfuscations = getStringLiteralObfuscations(literals);

        List<CtLocalVariable> localVariables = rootPackage.getElements(new TypeFilter<>(CtLocalVariable.class));
        System.out.println("# Local Variables: " + localVariables.size());
        renameLocalVariables(localVariables);

        List<CtParameterImpl> parameters = rootPackage.getElements(new TypeFilter<>(CtParameterImpl.class));
        System.out.println("# Parameters: " + parameters.size());
        renameParameters(parameters);

        List<CtField> fields = rootPackage.getElements(new TypeFilter<>(CtField.class));
        System.out.println("# Fields: " + fields.size());
        renameFields(fields);

        List<CtMethod> methods = rootPackage.getElements(new TypeFilter<>(CtMethod.class));
        System.out.println("# Methods: " + methods.size());
        renameMethods(methods);

        List<CtClass> classes = rootPackage.getElements(new TypeFilter<>(CtClass.class));
        List<CtInterface> interfaces = rootPackage.getElements(new TypeFilter<>(CtInterface.class));
        Map<String, String> classNames = new HashMap<>(); // Qualified name --> Obfuscated Name
        Map<String, String> classNewNames = new HashMap<>(); // Simple Name --> Obfuscated Name
        Map<String, String> interfaceNames = new HashMap<>(); // Qualified Name --> Obfuscated Name
        List<String> sortedClassSimpleNames = new ArrayList<>();
        for (CtClass c : classes) {
            String simpleName = c.getSimpleName();
            String qualifiedName = c.getQualifiedName().replace('$', '.');
            String newName = obfuscatedName();
            classNames.put(qualifiedName, newName);
            sortedClassSimpleNames.add(simpleName);
            classNewNames.put(simpleName, newName);
        }
        for (CtInterface c : interfaces) {
            String qualifiedName = c.getQualifiedName().replace('$', '.');
            String newName = obfuscatedName();
            interfaceNames.put(qualifiedName, newName);
        }

        // Sort names alphabetically in reverse order. This ensures that during
        // iteration substrings will come after the names that they are
        // substrings of.
        sortedClassSimpleNames.sort(Comparator.reverseOrder());
        List<CtClass> sortedClassNames = new ArrayList<>(classes);
        sortedClassNames.sort((o1, o2) -> {
            String qualifiedName1 = o1.getQualifiedName().replace('$', '.');
            String qualifiedName2 = o2.getQualifiedName().replace('$', '.');
            return -qualifiedName1.compareTo(qualifiedName2);
        });
        List<CtInterface> sortedInterfaceNames = new ArrayList<>(interfaces);
        sortedInterfaceNames.sort((o1, o2) -> {
            String qualifiedName1 = o1.getQualifiedName().replace('$', '.');
            String qualifiedName2 = o2.getQualifiedName().replace('$', '.');
            return -qualifiedName1.compareTo(qualifiedName2);
        });

        // Do the actual renaming
        for (CtClass c : sortedClassNames) {
            if (c.isAnonymous()) {
                continue;
            }
            String qualifiedName = c.getQualifiedName().replace('$', '.');
            c.setSimpleName(classNames.get(qualifiedName));
        }
        for (CtInterface c : sortedInterfaceNames) {
            if (c.isAnonymous()) {
                continue;
            }
            String qualifiedName = c.getQualifiedName().replace('$', '.');
            c.setSimpleName(interfaceNames.get(qualifiedName));
        }

        launcher.prettyprint();

        // All .java files to obfuscationMerge
        List<File> files = new ArrayList<>();
        // Directories to search for files in
        Stack<File> directories = new Stack<>();
        // Map of a file to a string of the filepath from the source root to it
        Map<File, String> parentPath = new HashMap<>();
        directories.push(tmpOutput);
        parentPath.put(tmpOutput, "");

        // Collect all .java files
        while (!directories.isEmpty()) {
            File curr = directories.pop();
            assert curr.isDirectory();

            for (File file : curr.listFiles()) {
                parentPath.put(file, parentPath.get(curr) + "/" + curr.getName());
                if (file.isDirectory()) {
                    directories.add(file);
                } else if (file.isFile() && file.getName().endsWith(".java")) {
                    files.add(file);
                }
            }
        }
        Collections.shuffle(files);

        // Rename classes/interfaces and merge files
        PrintStream outfile = new PrintStream(new File(mergedFilePath));
        List<String> sortedClassNameStrings = new ArrayList<>(classNames.keySet());
        sortedClassNameStrings.addAll(interfaceNames.keySet());
        sortedClassNameStrings.sort(Comparator.reverseOrder());
        for (File file : files) {
            Scanner infile = new Scanner(file);
            StringBuilder sb = new StringBuilder();

            while (infile.hasNextLine()) {
                String line = infile.nextLine();
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) {
                    sb.append('\n');
                    continue;
                }
                if (trimmedLine.startsWith("package") || trimmedLine.equals("@java.lang.Override")) {
                    continue;
                }
                if (line.contains("\"")) {
                    // Obfuscate string literals
                    for (Map.Entry<String, String> obfuscation : stringLiteralObfuscations.entrySet()) {
                        line = line.replace(obfuscation.getKey(), obfuscation.getValue());
                    }
                }
                line = line.replaceFirst("public class", "class");
                line = line.replaceFirst("public interface", "interface");
                for (String oldName : sortedClassNameStrings) {
                    if (classNames.containsKey(oldName)) {
                        line = line.replace(oldName, classNames.get(oldName));
                    } else {
                        line = line.replace(oldName, interfaceNames.get(oldName));
                    }
                    if (line.contains(".this.")) {
                        for (String simpleName : sortedClassSimpleNames) {
                            line = line.replace(simpleName + ".this.", classNewNames.get(simpleName) + ".this.");
                        }
                    }
                }
                sb.append(line).append('\n');
            }
            outfile.print(sb.toString());
        }
        outfile.close();
    }
}
