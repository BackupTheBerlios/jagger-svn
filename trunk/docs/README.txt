Groovy Starter is meant to help developers new to groovy to get started
by providing a way to have a groovy project up and running with a
minimum effort. 

GroovyQuickStart provides a default layout and a reusable gant script
that knows how to compile, test and package your project out of the box.


COMPILING YOUR PROJECT 

prompt> gant compile 

This target knows how to compile any source files that are available in
the default source location. Running out of the box should report the
succesful creation of the required build output folders and the
compilation of one sample java class and two sample groovy classes 


TESTING YOUR PROJECT 

prompt> gant test 

The test target will compile the source code in the src and test_src
folders, and then it will execute the unit tests located in the
tests_src folder. When you run this target out of the box it will
succesfully report the execution of two sample test classes 


PREPARING TO DISTRIBUTE YOUR PROJECT 

prompt> gant distro 

the distro target will create a distribution folder in your build_output
folder. This dist folder will contain a lib folder with all the jars
from the project lib folder, as well as the contents of the src folder
packaged as a jar. Also in the dist folder is your README.txt file and a
bin folder that contains the sample batch file provided with the
project. To test the distribution you can cd into your
%path%\groovyquickstart\build_output\dist\bin and run the file
“run.bat” or “run” in linux(still working on this one). This
launch script will print the help message: 

usage: runner [option] -h,--help Print out this message containing
help. -n,--name The name of the user to be greeted. -r,--run Runs some
target. -v,--version Print version information. 

To test the sample commands in the Runner class, enter the command
prompt>run -n "foo" -r. This will return the output: prompt> Hello
from the starter class foo 

This sample Runner class demonstrates how to use the CLI builder to read
parameters from the command line and to execute a class in the project
based on those parameters. 


PACKAGING YOUR PROJECT

prompt> gant package 

This target will create a zip file with the content of your dist folder
in the “\build_output\dist” folder 

Customizing GroovyQuickStart: When you use the default folders for your
project artifacts the gant script should work without modifications, If
you feel that you need to add or improve your build steps just modify
the gant build script to suit your needs. 

Eclipse Support: The GroovyQuickStart can be imported into Eclipse, once
you use the Eclipse import facilities you will need to update the
project build dependencies to point to the correct location of the
Groovy libraries. 


GROOVYQUICKSTART PROJECT LAYOUT

build.gant //the project script file VERSION.txt //a file used by the
build to pick up the version identifier bin |-run.bat //sample file that
can run the sample class docs |-README.txt //a place holder for your
documentation lib |-*.jar //your dependencies src |-java //java source
code |-groovy //groovy source code tests_src |-java //java tests
|-groovy //groovy tests 

bin: This folder contains a batch file that by default knows how to
execute a runner class that is provided with the project, and that can
be used to allow users to launch your groovy app. 

docs: a default place to drop documentation that will be packaged with
your project 

lib: a folder where the gant script will look for dependencies when
compiling and running tests in your project. this folder will also be
packaged as part of the distribution. 

src: This folder is where the project's gant script will look for
classes to compile, the gant script will compile the java classes in the
java folder first and then will use groovyc to compile the groovy
classes in the groovy folder 

tests_src This folder is where the project's gant script will look for
test to execute agains your project the default classpath will look for
dependencies in the folder that contained the compiled classes from the
src folder as well as the jars in the lib folder 

I hope you can take advantage of Groovy Quick Start Project to get
started on your groovy project and please feel free to contact me with
any questions at davilameister@gmail.com

