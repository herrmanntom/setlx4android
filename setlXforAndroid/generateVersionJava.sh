#!/bin/bash

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
    public final static String SETL_X_SHELL_VERSION = "${shellVersion}";
}
EOF

echo "own version       $shellVersion"

