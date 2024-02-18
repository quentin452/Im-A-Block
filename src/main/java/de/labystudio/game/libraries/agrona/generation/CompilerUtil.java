/*
 * Copyright 2014-2023 Real Logic Limited.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.labystudio.game.libraries.agrona.generation;

import de.labystudio.game.libraries.agrona.LangUtil;
import de.labystudio.game.libraries.agrona.SystemUtil;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.tools.*;

/**
 * Utilities for compiling Java source files at runtime.
 */
public final class CompilerUtil {

    /**
     * Temporary directory for files.
     */
    private static final String TEMP_DIR_NAME = SystemUtil.tmpDirName();

    private CompilerUtil() {}

    /**
     * Compile a {@link Map} of source files in-memory resulting in a {@link Class} which is named.
     *
     * @param className to return after compilation.
     * @param sources   to be compiled.
     * @return the named class that is the result of the compilation.
     * @throws ClassNotFoundException of the named class cannot be found.
     */
    public static Class<?> compileInMemory(final String className, final Map<String, CharSequence> sources)
        throws ClassNotFoundException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (null == compiler) {
            throw new IllegalStateException("JDK required to run tests. JRE is not sufficient.");
        }

        final JavaFileManager fileManager = new de.labystudio.game.libraries.agrona.generation.ClassFileManager<>(compiler.getStandardFileManager(null, null, null));
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final JavaCompiler.CompilationTask task = compiler
            .getTask(null, fileManager, diagnostics, null, null, wrap(sources));

        return compileAndLoad(className, diagnostics, fileManager, task);
    }

    /**
     * Compile a {@link Map} of source files on disk resulting in a {@link Class} which is named.
     *
     * @param className to return after compilation.
     * @param sources   to be compiled.
     * @return the named class that is the result of the compilation.
     * @throws ClassNotFoundException of the named class cannot be found.
     * @throws IOException            if an error occurs when writing to disk.
     */
    public static Class<?> compileOnDisk(final String className, final Map<String, CharSequence> sources)
        throws ClassNotFoundException, IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (null == compiler) {
            throw new IllegalStateException("JDK required to run tests. JRE is not sufficient.");
        }

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            final ArrayList<String> options = new ArrayList<>(
                Arrays
                    .asList("-classpath", System.getProperty("java.class.path") + File.pathSeparator + TEMP_DIR_NAME));

            final Collection<File> files = persist(sources);
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);
            final JavaCompiler.CompilationTask task = compiler
                .getTask(null, fileManager, diagnostics, options, null, compilationUnits);

            return compileAndLoad(className, diagnostics, fileManager, task);
        }
    }

    /**
     * Compile and load a class.
     *
     * @param className   name of the class to compile.
     * @param diagnostics attached to the compilation task.
     * @param fileManager to load compiled class from disk.
     * @param task        compilation task.
     * @return {@link Class} for the compiled class or {@code null} if compilation fails.
     * @throws ClassNotFoundException if compiled class was not loaded.
     */
    public static Class<?> compileAndLoad(final String className, final DiagnosticCollector<JavaFileObject> diagnostics,
        final JavaFileManager fileManager, final JavaCompiler.CompilationTask task) throws ClassNotFoundException {
        if (!compile(diagnostics, task)) {
            return null;
        }

        return fileManager.getClassLoader(null)
            .loadClass(className);
    }

    /**
     * Execute compilation task and report errors if it fails.
     *
     * @param diagnostics attached to the compilation task.
     * @param task        compilation to be executed.
     * @return {@code true} if compilation succeeds.
     */
    public static boolean compile(final DiagnosticCollector<JavaFileObject> diagnostics,
        final JavaCompiler.CompilationTask task) {
        final Boolean succeeded = task.call();

        if (!succeeded) {
            for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                System.err.println(diagnostic.getCode());
                System.err.println(diagnostic.getKind());

                final JavaFileObject source = diagnostic.getSource();
                System.err.printf(
                    "Line = %d, Col = %d, File = %s",
                    diagnostic.getLineNumber(),
                    diagnostic.getColumnNumber(),
                    source);

                System.err.println("Start: " + diagnostic.getStartPosition());
                System.err.println("End: " + diagnostic.getEndPosition());
                System.err.println("Pos: " + diagnostic.getPosition());

                try {
                    final String content = source.getCharContent(true)
                        .toString();
                    final int begin = content.lastIndexOf('\n', (int) diagnostic.getStartPosition());
                    final int end = content.indexOf('\n', (int) diagnostic.getEndPosition());
                    System.err.println(diagnostic.getMessage(null));
                    System.err.println(content.substring(Math.max(0, begin), end));
                } catch (final IOException ex) {
                    LangUtil.rethrowUnchecked(ex);
                }
            }
        }

        return succeeded;
    }

    /**
     * Persist source files to disc.
     *
     * @param sources to persist.
     * @return a collection of {@link File} objects pointing to the persisted sources.
     * @throws IOException in case of I/O errors.
     */
    public static Collection<File> persist(final Map<String, CharSequence> sources) throws IOException {
        final Collection<File> files = new ArrayList<>(sources.size());
        for (final Map.Entry<String, CharSequence> entry : sources.entrySet()) {
            final String fqClassName = entry.getKey();
            String className = fqClassName;
            Path path = Paths.get(TEMP_DIR_NAME);

            final int indexOfLastDot = fqClassName.lastIndexOf('.');
            if (indexOfLastDot != -1) {
                className = fqClassName.substring(indexOfLastDot + 1);

                path = Paths.get(
                    TEMP_DIR_NAME + fqClassName.substring(0, indexOfLastDot)
                        .replace('.', File.separatorChar));
                Files.createDirectories(path);
            }

            final File file = new File(path.toString(), className + ".java");
            files.add(file);

            try (FileWriter out = new FileWriter(file)) {
                out.append(entry.getValue());
                out.flush();
            }
        }

        return files;
    }

    private static Collection<de.labystudio.game.libraries.agrona.generation.CharSequenceJavaFileObject> wrap(final Map<String, CharSequence> sources) {
        return sources.entrySet()
            .stream()
            .map((e) -> new de.labystudio.game.libraries.agrona.generation.CharSequenceJavaFileObject(e.getKey(), e.getValue()))
            .collect(toList());
    }
}
