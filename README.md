# Java Source Code Obfuscator #
This utility obfuscates the Java source code found in a specified
directory. Obfuscation methods consist of:

* Merging all `.java` files into a single file.
* Renaming classes, interfaces, methods, parameters, fields, and
variables to random alphabetic strings.
* Replacing string literals with randomly padded base64 equivalents that
are decoded at runtime.

All obfuscation steps are randomized, so re-running this utility on the
same code multiple times will yield different results.

This utility supports inheritance, duplicate class names, and method
overloading.

## Usage ##
Run the `fatJar` gradle task to build. The `.jar` artifact will be in the
`build/libs/` directory. Execute as `java -jar <Obfuscate.jar> <source_root> <output_file>`.

## Instructions ##
For best results the source code should be in a package with a unique
name. Note that in some certain cases instances of `this()` in
constructors will be replaced by `super()`, causing compilation errors.
This can be fixed by simply renaming the method call back to `this`.

## Examples ##
The following are example outputs that this utility can produce.
1. This utility: [ObfuscatedMerger.java](https://gist.github.com/veylence/c9513bb60023c36fbe13bbb18866a00a)
2. My [pathfinding algorithm comparison](https://github.com/veylence/PathfindingComparison) visualization: [ObfuscatedPathfindingComparison.java](https://gist.github.com/veylence/a9d65cc31a4fb018c84b2d507a18e482)
3. FizzBuzz:

**Before**
```
public class FizzBuzz {
    public static final int LIMIT = 100;

    public static void main(String[] args) {
        for (int i = 1; i <= LIMIT; i++) {
            if (i % 15 == 0) {
                System.out.println("FizzBuzz");
            } else if (i % 3 == 0) {
                System.out.println("Fizz");
            } else if (i % 5 == 0) {
                System.out.println("Buzz");
            } else {
                System.out.println(i);
            }
        }
    }
}
```

**After**
```
class _mvb {
    public static final int _jyc = 100;

    public static void main(java.lang.String[] _hfn) {
        for (int _jms = 1; _jms <= _mvb._jyc; _jms++) {
            if ((_jms % 15) == 0) {
                java.lang.System.out.println(new String(java.util.Base64.getDecoder().decode("YWFhRml6ekJ1enphYQ==")).substring(3,11));
            } else if ((_jms % 3) == 0) {
                java.lang.System.out.println(new String(java.util.Base64.getDecoder().decode("YWFhYUZpenphYQ==")).substring(4,8));
            } else if ((_jms % 5) == 0) {
                java.lang.System.out.println(new String(java.util.Base64.getDecoder().decode("YUJ1enph")).substring(1,5));
            } else {
                java.lang.System.out.println(_jms);
            }
        }
    }
}
```