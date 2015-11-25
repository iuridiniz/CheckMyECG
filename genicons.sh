#!/bin/bash
BASEDIR=$( (cd -P "`dirname "$0"`" && pwd) )
RESDIR="$BASEDIR/app/src/main/res"
PREFIX="drawable-"

which convert >/dev/null;
[ $? != 0 ] && echo "missing 'convert'" && exit 1
which xcf2png >/dev/null
[ $? != 0 ] && echo "missing 'xcf2png'" && exit 1

gen_drawables() {
    filepath=${1}
    maxsize=${2:-"100%"}
    resdir=${3:-"$RESDIR"}
    prefix=${4:-"$PREFIX"}
    format=${5:-"png"}

    filename=$(basename "$filepath")
    extension="${filename##*.}"
    filename="${filename%.*}"

    [ -z "$filepath" ] && return 1

    case "$extension" in
        'xcf')
            xcf=1
            ;;
        *)
            xcf=0
        ;;
    esac

    case "$maxsize" in
        *%)
            percent=1
            maxsize=${maxsize%\%}
            scale=2
            ;;
        *)
            percent=0
            scale=0
            ;;
    esac

    mdpi_size=$(bc <<< "scale=$scale; $maxsize/4")
    hdpi_size=$(bc <<< "scale=$scale; $maxsize/4 + $maxsize/8")
    xhdpi_size=$(bc <<< "scale=$scale; $maxsize/2")
    xxhpi_size=$(bc <<< "scale=$scale; $maxsize/2 + $maxsize/4")
    xxxhpi_size=$maxsize

    for pair in \
        mdpi:$mdpi_size \
        hdpi:$hdpi_size \
        xhdpi:$xhdpi_size \
        xxhdpi:$xxhpi_size \
        xxxhdpi:$xxxhpi_size \
    ; do
        suffix=$(echo $pair | cut -d: -f 1)
        size=$(echo $pair | cut -d: -f 2)
        outfile="${resdir}/${prefix}${suffix}/${filename}.${format}"
        if [ "$percent" -eq 1 ]; then
            convert_opts="-resize ${size}%"
        else
            #convert_opts="-size ${size}x${size}"
            convert_opts="-resize ${size}"
        fi
        mkdir -p $(dirname "$outfile")
        if [ "$xcf" -eq 1 ]; then
            xcf2png "$filepath" | \
                convert - $convert_opts "${format}":- > "${outfile}"
        else
            convert $filepath $convert_opts "${format}":- > "${outfile}"
        fi
    done
}

# generate drawables
gen_drawables "$BASEDIR/ic_launcher.xcf" 100%
gen_drawables "$BASEDIR/ic_circle.svg" 96
gen_drawables "$BASEDIR/ic_done.svg" 96
