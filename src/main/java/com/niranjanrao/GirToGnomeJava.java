package com.niranjanrao;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.sun.codemodel.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GirToGnomeJava {

	private static final GirNameSpaceContext GIR_NAME_SPACE_CONTEXT = new GirNameSpaceContext();

	public static void main(final String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("Usage GirToGnomeJava <gir path> <java-gnome-branch-path>");
			System.exit(1);
		}
		final String java_gnome_path = args[1];

		File f = new File(java_gnome_path + "/src/bindings");
		if (f.exists() == false) {
			f.mkdirs();
		}
		f = new File(java_gnome_path + "/src/defs");
		if (f.exists() == false) {
			f.mkdirs();
		}
		final String girPath = args[0];

		processGir(girPath, java_gnome_path);
	}

	private static void processGir(final String inputGirPath, final String javaGnomeOutputDir) throws Exception {
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true); // never forget this!
		final DocumentBuilder builder = factory.newDocumentBuilder();
		final Document doc = builder.parse(inputGirPath);
		NodeList list = executeXPathList(doc, "//ns:class[@name='DBusObjectSkeleton']");
		list = executeXPathList(doc, "//ns:class");

		final String nameSpace = executeXPathString(doc, "//ns:namespace/@name");
		System.out.println("Namespace: " + nameSpace);

		// final String include = executeXPathString(doc, "//c:include/@name");
		final NodeList includeNList = executeXPathList(doc, "//c:include/@name");
		final ArrayList<String> includeSList = new ArrayList<String>();
		for (int iClass = 0, iClassMax = includeNList.getLength(); iClass < iClassMax; iClass++) {
			includeSList.add(includeNList.item(iClass).getTextContent());
		}
		final String[] include = includeSList.toArray(new String[0]);
		Iterator<String> iterator = includeSList.iterator();
		StringBuilder files= new StringBuilder();
		while (iterator.hasNext()) {
			Object next =  iterator.next();
			files.append(next.toString());
			if (iterator.hasNext()) {
				files.append(", ");
			}
		}
		System.out.printf("Include files: %s%n", files);

		final JCodeModel model = new JCodeModel();
		generatePlumbing(model, nameSpace);

		for (int iClass = 0, iClassMax = list.getLength(); iClass < iClassMax; iClass++) {
			generateDef(javaGnomeOutputDir, model, list.item(iClass), nameSpace, include);
		}

		if (list.getLength() != 1) {
			list = executeXPathList(doc, "//ns:interface");
			for (int iClass = 0, iClassMax = list.getLength(); iClass < iClassMax; iClass++) {
				generateDef(javaGnomeOutputDir, model, list.item(iClass), nameSpace, include);
			}

			list = executeXPathList(doc, "//ns:enumeration");
			for (int iClass = 0, iClassMax = list.getLength(); iClass < iClassMax; iClass++) {
				generateEnumDef(javaGnomeOutputDir, model, list.item(iClass), nameSpace, include);
			}

			list = executeXPathList(doc, "//ns:record");
			for (int iClass = 0, iClassMax = list.getLength(); iClass < iClassMax; iClass++) {
				try {
					generateDef(javaGnomeOutputDir, model, list.item(iClass), nameSpace, include);
				} catch (JClassAlreadyExistsException e) {
				}
			}
		}

		model.build(new File(javaGnomeOutputDir + "/src/bindings"));

	}

	private static void generateEnumDef(final String javaGnomeOutputDir, final JCodeModel model, final Node item, final String nameSpace,
			final String... include) throws Exception {

		final StringBuffer buff = new StringBuffer();
		final String name = executeXPathString(item, "./@name");

		final JDefinedClass cls = model._class(getPackageName(nameSpace) + "." + name);
		cls._extends(model.directClass("org.freedesktop.bindings.Constant"));

		final JMethod constructorMethod = cls.constructor(JMod.PRIVATE);
		final JVar ordinal = constructorMethod.param(model.INT, "ordinal");
		final JVar nickname = constructorMethod.param(String.class, "nickname");
		final JInvocation superInvocation = constructorMethod.body().invoke("super");
		superInvocation.arg(ordinal);
		superInvocation.arg(nickname);

		final String plumbing = executeXPathString(item, "./@c:type");
		startSExp(buff, "define-enum", name);

		addSExp(buff, 2, "in-module", nameSpace);

		addSExp(buff, 2, "c-name", plumbing);

		buff.append("  (values").append(NL);
		final NodeList members = executeXPathList(item, "./ns:member");
		for (int i = 0, iMax = members.getLength(); i < iMax; i++) {
			final Node member = members.item(i);
			buff.append("    '(\"").append(executeXPathString(member, "./@name")).append("\" \"").append(executeXPathString(member, "./@c:identifier"))
					.append("\")").append(NL);
		}
		buff.append("  )").append(NL);
		endSExp(buff);
		final PrintWriter fos = new PrintWriter(javaGnomeOutputDir + "/src/defs/" + nameSpace + name + ".defs");
		fos.write(buff.toString());
		fos.flush();
		fos.close();
	}

	private static void generatePlumbing(final JCodeModel model, String nameSpace) throws Exception {
		final int mod = JMod.PUBLIC | JMod.ABSTRACT;

		nameSpace = getPackageName(nameSpace);
		final JDefinedClass cls = model._class(mod, nameSpace + ".Plumbing", ClassType.CLASS);

		cls._extends(model.directClass("org.gnome.gtk.Plumbing"));
	}

	private static String getPackageName(String nameSpace) {
		nameSpace = firstLetterLower(nameSpace);

		return "org.gnome." + nameSpace;
	}

	private static String firstLetterLower(String nameSpace) {
		nameSpace = Character.toLowerCase(nameSpace.charAt(0)) + (nameSpace.length() > 1 ? nameSpace.substring(1) : "");
		return nameSpace;
	}

	private static String executeXPathString(final Node node, final String string) throws Exception {
		final Node result = (Node) executeXPath(node, string, XPathConstants.NODE);
		return result != null ? result.getTextContent() : null;
	}

	static final String NL = System.lineSeparator();

	private static void generateDef(final String javaGnomeOutputDir, final JCodeModel model, final Node item, final String nameSpace, final String... include)
			throws Exception {
		final StringBuffer buff = new StringBuffer();

		final String cname = executeXPathString(item, "./@name");

		String parent = executeXPathString(item, "./@parent");

		if (parent == null) {
			parent = "GObject.Object";
		}

		if (parent.equals("Gtk.Container")) {
			parent = "org.gnome.gtk.Container";
		} else if (parent.equals("GObject.Object")) {
			parent = "org.gnome.glib.Object";
		}
		System.out.println(cname + " extends " + parent);
		final JDefinedClass cls = model._class(getPackageName(nameSpace) + "." + cname);
		cls._extends(model.directClass(parent));
		String plumbing = executeXPathString(item, "./@c:type");
		if (plumbing == null) {
			plumbing = cname;
		}
		startSExp(buff, "define-object", cname);

		addSExp(buff, 2, "in-module", nameSpace);
		for (final String s : include) {
			addSExp(buff, 2, "import-header", s);
		}
		addSExp(buff, 2, "parent", buildParentString(parent));

		addSExp(buff, 2, "c-name", plumbing);

		endSExp(buff);

		generateConstructors(buff, model, parent, nameSpace, cls, item, cname, plumbing);

		generateMethods(buff, model, parent, nameSpace, cls, item, plumbing);

		final PrintWriter fos = new PrintWriter(javaGnomeOutputDir + "/src/defs/" + nameSpace + cname + ".defs");
		fos.write(buff.toString());
		fos.flush();
		fos.close();

	}

	private static void generateMethods(final StringBuffer buff, final JCodeModel model, final String parent, final String nameSpace, final JDefinedClass cls,
			final Node item, final String cname) throws Exception {
		// NodeList methods = executeXPathList(item,
		// "./ns:method[@name='load_uri']");
		final HashMap<String, String> methodMap = new HashMap<String, String>();
		final NodeList methods = executeXPathList(item, "./ns:method|./ns:virtual-method");
		for (int i = 0, iMax = methods.getLength(); i < iMax; i++) {
			generateMethod(buff, model, parent, nameSpace, cls, methods.item(i), cname, methodMap);
		}
	}

	private static void generateMethod(final StringBuffer buff, final JCodeModel model, final String parent, final String nameSpace, final JDefinedClass cls,
			final Node method, final String cname, final HashMap<String, String> methodMap) throws Exception {

		final String methodName = executeXPathString(method, "./@name");
		if (methodMap.containsKey(methodName)) {
			return;
		}
		methodMap.put(methodName, methodName);
		startSExp(buff, "define-method", methodName);

		addSExp(buff, 2, "of-object", cname);
		String plumbing = executeXPathString(method, "./@c:identifier");
		if (plumbing == null) {
			plumbing = methodName;
		}
		addSExp(buff, 2, "c-name", plumbing);
		final String type = executeXPathString(method, "./ns:return-value/ns:type/@c:type");
		String retType = buildParentString(type);
		if (retType == null || retType.equals("void")) {
			retType = "none";
		}
		addSExp(buff, 2, "return-type", retType);

		// JClass jRetType = model.directClass(getJavaType(retType));
		// JMethod jm = cls.method(JMod.PUBLIC, jRetType, methodName);

		generateParameters(buff, method);
		endSExp(buff);

	}

	private static void generateParameters(final StringBuffer buff, final Node method) throws Exception {
		buff.append("  (parameters").append(NL);
		final NodeList params = executeXPathList(method, "./ns:parameters/ns:parameter");
		for (int i = 0, iMax = params.getLength(); i < iMax; i++) {
			generateParameter(buff, params.item(i));
		}

		final String throwsVal = executeXPathString(method, "./@throws");
		if (throwsVal != null && throwsVal.equals("1")) {
			buff.append("    '(\"").append("GError**").append('"');
			buff.append(" \"").append("error").append("\")").append(NL);
		}
		buff.append("  )").append(NL);
	}

	private static void generateParameter(final StringBuffer buff, final Node param) throws Exception {
		buff.append("    '(\"").append(executeXPathString(param, "./ns:type/@c:type")).append('"');
		buff.append(" \"").append("p" + executeXPathString(param, "./@name")).append("\")").append(NL);
	}

	private static void generateConstructors(final StringBuffer buff, final JCodeModel model, final String parent, final String nameSpace,
			final JDefinedClass cls, final Node item, final String cname, final String plumbing) throws Exception {
		final NodeList list = executeXPathList(item, "./ns:constructor");

		for (int i = 0, iMax = list.getLength(); i < iMax; i++) {
			generateConstructor(buff, model, parent, nameSpace, cls, list.item(i), cname, plumbing);
		}
		final JMethod constructorMethod = cls.constructor(JMod.PROTECTED);
		final JVar pointer = constructorMethod.param(model.LONG, "pointer");
		final JInvocation superInvocation = constructorMethod.body().invoke("super");
		superInvocation.arg(pointer);

	}

	private static void generateConstructor(final StringBuffer buff, final JCodeModel model, final String parent, final String nameSpace,
			final JDefinedClass cls, final Node constructor, final String cname, final String plumbing2) throws Exception {
		startSExp(buff, "define-function", cname + "_" + executeXPathString(constructor, "./@name"));

		addSExp(buff, 2, "is-constructor-of", plumbing2);
		final String methodName = executeXPathString(constructor, "./@c:identifier");
		addSExp(buff, 2, "c-name", methodName);
		// addSExp(buff, 2, "caller-owns-return")
		addSExp(buff, 2, "return-type", buildParentString(executeXPathString(constructor, "./ns:return-value/ns:type/@c:type")));

		generateParameters(buff, constructor);
		endSExp(buff);

		final JMethod constructorMethod = cls.constructor(JMod.PUBLIC);
		final JClass plumbing = model.directClass(getPackageName(nameSpace) + "." + plumbing2);
		final JInvocation staticInvoke = plumbing.staticInvoke("create" + mapToJavaMethodName(cname));
		final NodeList params = executeXPathList(constructor, "./ns:parameters/ns:parameter");
		Node param;
		JVar jvar;
		for (int i = 0, iMax = params.getLength(); i < iMax; i++) {
			param = params.item(i);
			String paramName = executeXPathString(param, "./@name");
			if (paramName == null) {
				continue;
			}
			paramName = "p" + paramName;

			final String cType = getJavaType(executeXPathString(param, "./ns:type/@c:type"), nameSpace);
			if (cType == null) {
				continue;
			}
			jvar = constructorMethod.param(model.directClass(cType), paramName);
			staticInvoke.arg(jvar);
		}
		final JInvocation superInvocation = constructorMethod.body().invoke("super");

		superInvocation.arg(staticInvoke);
	}

	private static String getJavaType(String type, final String nameSpace) {
		if (type == null) {
			return null;
		}
		type = type.replaceAll("const\\s+", "");
		final String matcher = "(g?)char\\s*\\*";
		if (type.matches(matcher)) {
			return "java.lang.String";
		}

		if (type.matches("gssize|guint|gint")) {
			return "int";
		}
		if (type.matches("gunit16")) {
			return "char";
		}
		if (type.matches("gsize")) {
			return "long";
		}
		if (type.matches("gboolean")) {
			return "boolean";
		}
		if (type.matches("gpointer")) {
			return "org.freedesktop.bindings.Pointer";
		}
		type = type.replace(nameSpace, "");
		type = type.replace("*", "");
		if (type.equals("GObject")) {
			return "org.gnome.glib.Object";
		}
		if (type.startsWith("G")) {
			type = type.substring(1);
		}
		return type;
	}

	private static String firstLetterUpper(String nameSpace) {
		nameSpace = Character.toUpperCase(nameSpace.charAt(0)) + (nameSpace.length() > 1 ? nameSpace.substring(1) : "");
		return nameSpace;
	}

	static String mapToJavaMethodName(String methodName) {
		methodName = methodName.replace("webkit_", "");
		methodName = methodName.replace("_new_", "_");
		methodName = methodName.replace("_new", "_");
		methodName = methodName.replace("g_", "");

		final String[] arr = methodName.split("_");
		final StringBuffer buff = new StringBuffer();
		for (final String s : arr) {
			buff.append(firstLetterUpper(s));
		}
		return buff.toString();
	}

	private static String buildParentString(final String name) {
		if (name == null) {
			return name;
		}
		final int index = name.indexOf("Gtk.");
		if (index != -1) {
			return name.substring(index + "Gtk.".length());
		}
		return name;
	}

	private static void addSExp(final StringBuffer buff, final int indent, final String key, final String... value) {
		for (int i = 0; i < indent; i++) {
			buff.append(" ");
		}

		buff.append("(").append(key).append(" ").append(joinArray(value)).append(")").append(NL);
	}

	static String joinArray(final String... array) {
		final StringBuffer buf = new StringBuffer();
		final int startIndex = 0, endIndex = array.length;
		final char separator = ',';

		for (int i = startIndex; i < endIndex; i++) {
			if (i > startIndex) {
				buf.append(separator);
				buf.append(' ');
			}
			if (array[i] != null) {
				buf.append('"');
				buf.append(array[i]);
				buf.append('"');
			}
		}
		return buf.toString();
	}

	private static void endSExp(final StringBuffer buff) {
		buff.append(")").append(NL).append(NL);

	}

	private static void startSExp(final StringBuffer buff, final String key, final String value) {
		buff.append("(").append(key).append(" ").append(value).append(NL);

	}

	private static NodeList executeXPathList(final Node doc, final String string) throws Exception {
		return (NodeList) executeXPath(doc, string, XPathConstants.NODESET);
	}

	static final HashMap<String, XPathExpression> expressionMap;
	static final XPath xpath;
	static {
		final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

		expressionMap = new HashMap<String, XPathExpression>();
		xpath = XPATH_FACTORY.newXPath();
		xpath.setNamespaceContext(GIR_NAME_SPACE_CONTEXT);
	}

	private static Object executeXPath(final Node doc, final String string, final QName retType) throws XPathExpressionException {
		XPathExpression expr = expressionMap.get(string);
		if (expr == null) {
			expr = xpath.compile(string);
			expressionMap.put(string, expr);
		}
		return expr.evaluate(doc, retType);
	}

	private static class GirNameSpaceContext implements NamespaceContext {

		public String getNamespaceURI(final String prefix) {
			if ("ns".equals(prefix)) {
				return "http://www.gtk.org/introspection/core/1.0";
			}
			if ("c".equals(prefix)) {
				return "http://www.gtk.org/introspection/c/1.0";
			}
			if ("glib".equals(prefix)) {
				return "http://www.gtk.org/introspection/glib/1.0";
			}
			return null;
		}

		public String getPrefix(final String namespaceURI) {
			return null;
		}

		public Iterator getPrefixes(final String namespaceURI) {
			return null;
		}

	}

}
