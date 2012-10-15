GirToGnomeJava
==============

Creates java bindings from .gir files to java

Building GirToGnomeJava converter
Pull GirToGnomeJava repository from Github.

cd to directory where you pulled sources.
Execute command

mvn package


Running the converter:
Create a new branch from mainline
bzr branch mainline/ girtest

Run the makefile generator
cd girtest
./configure

Build the branch
Make sure you have required libraries installed as per java-gnome documentation. 
For my ubnuntu machine, I had to change build/faster to successfully compile. 
Open build/faster file, add unique-3.0 webkitgtk-3.0 to GNOME_MOUDLES list

Run make to ensure that it compiles first.

make



Run the GirToGnomeJava class. It needs two files on the command line
1. Gir file to convert e.g. /usr/share/gir-1.0/WebKit-3.0.gir
2. Branch file name /home/niranjan/work/tools/java-gnome/girtest

From the directory where you cloned the covnerter
java -jar target/girToGnomeJava.jar /usr/share/gir-1.0/WebKit-3.0.gir /home/niranjan/work/tools/java-gnome/girtest

cd back to girtest. The generator generates the def files under src/defs and binding files under src/bindings.

Run make again

make


You should get one compiler error
generated/bindings/org/gnome/webKit/WebKitDOMTimeRanges.c:79:11: error: ‘FIXME’ undeclared (first use in this function)

Open the file, and replace FIXME with 0

Run make again
make
