#!/bin/bash

baseVersion="$(java -jar setlXforAndroid/libs/setlX-j1.6.jar --version | tail -n 1)"
baseVersion=${baseVersion#*": "}
baseVersion=${baseVersion%")"}
shellVersion="$(git describe --abbrev=40 --always --long --dirty)"
shellVersion=${shellVersion#*"_A"}

cat > setlXforAndroid/src/org/randoom/setlxUI/android/SourceVersion.java << EOF
package org.randoom.setlxUI.android;

public class SourceVersion {
    public final static String SETL_X_BASE_VERSION = "$baseVersion";
    public final static String SETL_X_SHELL_VERSION = "$shellVersion";
}
EOF

echo extracted version $sourceVersion

