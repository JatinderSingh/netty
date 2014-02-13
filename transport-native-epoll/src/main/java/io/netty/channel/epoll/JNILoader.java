/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;


import io.netty.util.internal.SystemPropertyUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Helper class to load JNI resources.
 *
 * TODO: Maybe move this to io.netty.util.internal
 */
final class JNILoader {

    private static final Pattern REPLACE = Pattern.compile("\\W+");

    private JNILoader() {
        // Utility
    }

    /**
     * Load the given library with the specified {@link java.lang.ClassLoader}
     */
    static void load(String name, ClassLoader loader) {
        String libname = System.mapLibraryName(name);
        String path = "META-INF/native/" + osIdentifier() + bitMode() + '/' + libname;

        URL url = loader.getResource(path);
        if (url == null) {
            // Fall back to normal loading of JNI stuff
            System.loadLibrary(name);
        } else {
            String unpackDirectory = SystemPropertyUtil.get("io.netty.jniloader.unpackdir",
                    SystemPropertyUtil.get("java.io.tmpdir"));

            int index = libname.lastIndexOf('.');
            String prefix = libname.substring(0, index);
            String suffix = libname.substring(index, libname.length());
            InputStream in = null;
            OutputStream out = null;
            File tmpFile = null;
            try {
                File dir = new File(unpackDirectory);
                if (!dir.exists()) {
                    // ok to ignore as createTempFile will take care
                    dir.mkdir();
                }
                tmpFile = File.createTempFile(prefix, suffix, dir);
                in = url.openStream();
                out = new FileOutputStream(tmpFile);

                byte[] buffer = new byte[1024];

                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                out.flush();

                System.load(tmpFile.getPath());
            } catch (IOException e) {
                throw new UnsatisfiedLinkError("Could not load library" + name + ": " + e.getMessage());
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignore) {
                        // ignore
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignore) {
                        // ignore
                    }
                }
                if (tmpFile != null) {
                    if (!tmpFile.delete()) {
                        tmpFile.deleteOnExit();
                    }
                }
            }
        }
    }

    private static String osIdentifier() {
        String name = System.getProperty("os.name").toLowerCase(Locale.UK).trim();
        if (name.startsWith("win")) {
            return "windows";
        }
        if (name.startsWith("mac os x")) {
            return "osx";
        }
        if (name.startsWith("linux")) {
            return "linux";
        }

        return REPLACE.matcher(name).replaceAll("_");
    }

    private static int bitMode() {
        String prop = System.getProperty("sun.arch.data.model");
        if (prop == null) {
            prop = System.getProperty("com.ibm.vm.bitmode");
        }
        if (prop != null) {
            return Integer.parseInt(prop);
        }
        throw new IllegalStateException("Unable to detect bitmode");
    }
}
