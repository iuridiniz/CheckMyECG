#!/bin/sh
BASEDIR=$( (cd -P "`dirname "$0"`" && pwd) )
RESDIR="$BASEDIR/app/src/main/res"
PREFIX="drawable-"
OUTPUTFILE="ic_launcher.png"
SOURCE="$BASEDIR/icon.xcf"

which convert >/dev/null || echo "missing 'convert'"
which xcf2png >/dev/null || echo "missing 'xcf2png'"

for i in mdpi:25% hdpi:37.5% xhdpi:50% xxhdpi:75% xxxhdpi:100%; do 
    suffix=$(echo $i | cut -d: -f 1)
    percent=$(echo $i | cut -d: -f 2)
    OUTPUTDIR="$RESDIR/${PREFIX}${suffix}"
    mkdir -p "$OUTPUTDIR" 2>/dev/null
    xcf2png "$SOURCE" | \
        convert - -resize "$percent" png:- > "$OUTPUTDIR/$OUTPUTFILE"
done
