#!/bin/bash

# discover setlX library
setlXLibrary="nil"
while read -r -d $'\0' path; do
    setlXLibrary="$path"
done < <( find -L "." -name "setlX-core-*.jar" -print0 )

# display its data
baseVersion="unknown"
afterImplementation=0
if [[ "$setlXLibrary" != "nil" ]]; then
    while IFS= read line; do
        if [[ "$line" =~ ^Implementation-Build: ]]; then
            baseVersion="$line"
            afterImplementation=1
        elif [[ "$afterImplementation" == 1 && "$line" =~ ^[[:space:]] ]]; then
            line=${line#" "}
            baseVersion="$baseVersion$line"
        else
            afterImplementation=0
        fi
    done < <( unzip -q -c "$setlXLibrary" "META-INF/MANIFEST.MF" | sed 's/\r$//' )
else
    echo "could not find setlX-core dependency" >&2
    exit 1
fi

baseVersion=${baseVersion#*": v"}
shellVersion="$(git describe --abbrev=40 --always --long --dirty)"
shellVersion=${shellVersion#*"_A"}

mainClassLocation="unknown"
while read -r -d $'\0' path; do
    mainClassLocation="$path"
done < <( find -L "." -name "SetlXforAndroidActivity.java" -print0 )

package=$(cat "$mainClassLocation" | head -n 1)
cat > "$(dirname "$mainClassLocation")/SourceVersion.java" << EOF
${package}

public class SourceVersion {
    public final static String SETL_X_BASE_VERSION = "${baseVersion}";
    public final static String SETL_X_SHELL_VERSION = "${shellVersion}";
}
EOF

echo "extracted version $baseVersion"
echo "own version       $shellVersion"

