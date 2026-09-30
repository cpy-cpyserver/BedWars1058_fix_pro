/*
 * Reverts the maven-shade relocation of the sidebar library, so the classes extracted from the
 * official BedWars1058 release jar can be used as a normal dependency again.
 *
 * Usage:
 *   javac -cp asm-9.5.jar:asm-commons-9.5.jar -d out libs/tools/DeRelocator.java
 *   java -cp out:asm-9.5.jar:asm-commons-9.5.jar DeRelocator <src-dir> <dst-dir>
 *
 * where <src-dir> contains the extracted 'com/andrei1058/bedwars/libs/sidebar' classes.
 * See libs/README.md for the full extraction procedure.
 */

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class DeRelocator {

    private static final String FROM_INTERNAL = "com/andrei1058/bedwars/libs/sidebar";
    private static final String TO_INTERNAL = "com/andrei1058/spigot/sidebar";
    private static final String FROM_DOTTED = "com.andrei1058.bedwars.libs.sidebar";
    private static final String TO_DOTTED = "com.andrei1058.spigot.sidebar";

    public static void main(String[] args) throws Exception {
        Path src = Paths.get(args[0]);
        Path dst = Paths.get(args[1]);

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                if (internalName.startsWith(FROM_INTERNAL)) {
                    return TO_INTERNAL + internalName.substring(FROM_INTERNAL.length());
                }
                return internalName;
            }

            @Override
            public Object mapValue(Object value) {
                if (value instanceof String) {
                    String s = (String) value;
                    if (s.startsWith(FROM_DOTTED)) return TO_DOTTED + s.substring(FROM_DOTTED.length());
                    if (s.startsWith(FROM_INTERNAL)) return TO_INTERNAL + s.substring(FROM_INTERNAL.length());
                    return s;
                }
                return super.mapValue(value);
            }
        };

        int[] count = {0};
        try (Stream<Path> paths = Files.walk(src)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                try {
                    byte[] input = Files.readAllBytes(p);
                    ClassReader reader = new ClassReader(input);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, remapper), 0);
                    String relative = src.relativize(p).toString().replace(File.separatorChar, '/');
                    if (relative.startsWith(FROM_INTERNAL)) {
                        relative = TO_INTERNAL + relative.substring(FROM_INTERNAL.length());
                    }
                    Path out = dst.resolve(relative);
                    Files.createDirectories(out.getParent());
                    Files.write(out, writer.toByteArray());
                    count[0]++;
                } catch (Exception e) {
                    throw new RuntimeException("Failed on " + p, e);
                }
            });
        }
        System.out.println("de-relocated classes: " + count[0]);
    }
}
