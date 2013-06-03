package com.niranjanrao;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;

public class GeneratePlumbing {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		// if (args.length != 2)
		// {
		// System.err.println("Usage <GeneratePlumbing <input class directory> <output dir>"
		// );
		// System.err.println("Class directory is full path to compiled classes directory where webkit classes reside");
		// }

		File classPath = new File(
				"/home/niranjan/work/tools/java-gnome/fin-gen/tmp/bindings/");
		URI url = classPath.toURI();
		URL[] urls = new URL[] { url.toURL() };
		ClassLoader cl = new URLClassLoader(urls,
				GeneratePlumbing.class.getClassLoader());

		IOFileFilter fileFilter = new IOFileFilter() {

			public boolean accept(File file) {
				return file.getName().matches("WebKit([^.]+)\\.class$");
			}

			public boolean accept(File dir, String name) {
				return name.matches("WebKit([^.]+)\\.class$");
			}
		};
		Collection<File> files = FileUtils.listFiles(classPath, fileFilter,
				TrueFileFilter.INSTANCE);

		JCodeModel model = new JCodeModel();
		for (File f : files) {
			createClass(model, cl, f);
		}
		model.build(new File("/tmp/out"));
	}

	private static void createClass(JCodeModel model, ClassLoader cl,
			File classFile) throws Exception {

		String toGenerate = classFile.getName();
		String baseClassName = toGenerate.replaceAll("^WebKit([^.]+)\\.class",
				"$1");
		toGenerate = "org.gnome.webKit." + baseClassName;
		System.out.println("Processing" + classFile.getName() + " to "
				+ toGenerate);

		Class<?> wClass = cl.loadClass("org.gnome.webKit.WebKit"
				+ baseClassName);
		// + baseClassName);
		JDefinedClass defined = model._class(toGenerate);

		System.out.println("Loaded class " + wClass);
		Method[] methods = wClass.getDeclaredMethods();
		Method method;
		JClass plumbing = model.directClass(wClass.getCanonicalName());

		for (int i = 0; i < methods.length; i++) {
			method = methods[i];
			if (Modifier.isNative(method.getModifiers())) {
				// System.out.println("Skipping " + method.toString());
				continue;
			}
			generateMethod(model, defined, plumbing, method);
		}

		generateConstructors(cl, toGenerate, defined, model);
	}

	private static void generateConstructors(ClassLoader cl, String toGenerate,
			JDefinedClass defined, JCodeModel model) throws Exception {
		Class<?> wClass = cl.loadClass(toGenerate);

		defined._extends(wClass.getSuperclass());
		Constructor<?>[] constructors = wClass.getConstructors();
		if (constructors.length > 1) {
			System.err.println("Warning: " + toGenerate
					+ " has more than one constructor");
		}
		for (Constructor<?> c : constructors) {
			JMethod constructorMethod = defined.constructor(c.getModifiers());
			Class<?>[] parameterTypes = c.getParameterTypes();
			for (int i = 1; i < parameterTypes.length; i++) // i = 1 is not a
			// mistake, we want to
			// start from 1
			{
				constructorMethod.param(parameterTypes[i], "arg" + i);
			}
		}

		JMethod constructorMethod = defined.constructor(JMod.PROTECTED);
		JVar pointer = constructorMethod.param(model.LONG, "pointer");
		JInvocation superInvocation = constructorMethod.body().invoke("super");
		superInvocation.arg(pointer);

	}

	private static void generateMethod(JCodeModel model, JDefinedClass defined,
			JClass plumbing, Method method) {
		JMethod genMethod = defined.method(JMod.PUBLIC, method.getReturnType(),
				method.getName());
		Class<?>[] parameterTypes = method.getParameterTypes();
		JInvocation invocation = plumbing.staticInvoke(method.getName());
		invocation.arg(JExpr.ref("this"));

		for (int i = 1; i < parameterTypes.length; i++) // i = 1 is not a
														// mistake, we want to
														// start from 1
		{
			invocation.arg(genMethod.param(parameterTypes[i], "arg" + i));

		}

		if (!method.getReturnType().equals(Void.TYPE)) {
			genMethod.body()._return(invocation);
		} else {
			genMethod.body().add(invocation);
		}
	}
}
