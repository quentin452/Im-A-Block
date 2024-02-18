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

import java.net.URI;

import javax.tools.SimpleJavaFileObject;

/**
 * An implementation of a {@link SimpleJavaFileObject} which stores the content in a {@link CharSequence}.
 */
public class CharSequenceJavaFileObject extends SimpleJavaFileObject {

    private final CharSequence sourceCode;

    /**
     * Create file object from class source code.
     *
     * @param className  name of the class.
     * @param sourceCode of the class.
     */
    public CharSequenceJavaFileObject(final String className, final CharSequence sourceCode) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.sourceCode = sourceCode;
    }

    /**
     * {@inheritDoc}
     */
    public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
        return sourceCode;
    }
}
