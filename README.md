# GirToGnomeJava

Creates java bindings from .gir files to defs and bindings for java-gnome binding generator

## Building GirToGnomeJava converter:

1. Clone this repo
2. cd clone dir
3. Build with maven

    mvn package

## Run the GirToGnomeJava: 

It needs two params:

* Gir file to convert e.g. /usr/share/gir-1.0/WebKit-3.0.gir
* dest dir. You can use directly the java-gnome repo to add defs /path/to/java-gnome

        java -jar target/girToGnomeJava.jar /usr/share/gir-1.0/WebKit-3.0.gir /path/to/java-gnome

It generates the def files under `src/defs` and binding files under `src/bindings` as java-gnome expects

## Running the binding generator:

1. cd to /path/to/java-gnome

Depending on module, we need to add dependencies. Example for webkit

Open `build/faster` file, add `unique-3.0` `webkitgtk-3.0` to `GNOME_MODULES` list

    ./configure
    make

### Errors

If you find this error:

> generated/bindings/org/gnome/webKit/WebKitDOMTimeRanges.c:79:11: error: ‘FIXME’ undeclared (first use in this function)

Open the file, and replace FIXME with 0, and build again

