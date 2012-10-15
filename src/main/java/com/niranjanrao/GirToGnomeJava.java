package com.niranjanrao;

import java.io.File;
import java.io.PrintWriter;
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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;

public class GirToGnomeJava {

	private static final GirNameSpaceContext GIR_NAME_SPACE_CONTEXT = new GirNameSpaceContext();

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out
					.println("Usage GirToGnomeJava <gir path> <java-gnome-branch-path>");
			System.exit(1);
		}
		String java_gnome_path = args[1];
		String girPath = args[0];

		processGir(girPath, java_gnome_path);
	}

	private static void processGir(String inputGirPath,
			String javaGnomeOutputDir) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true); // never forget this!
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(inputGirPath);
		NodeList list = executeXPathList(doc,
				"//ns:class[@name='WebHistoryItem']");
		list = executeXPathList(doc, "//ns:class");

		String nameSpace = executeXPathString(doc, "//ns:namespace/@name");
		System.out.println("Namespace: " + nameSpace);

		String include = executeXPathString(doc, "//c:include/@name");
		System.out.println("Include files: " + include);

		JCodeModel model = new JCodeModel();
		generatePlumbing(model, nameSpace);

		for (int iClass = 0, iClassMax = list.getLength(); iClass < iClassMax; iClass++) {
			generateDef(javaGnomeOutputDir, model, list.item(iClass),
					nameSpace, include);
		}

		list = executeXPathList(doc, "//ns:enumeration");
		for (int iClass = 0, iClassMax = list.getLength(); iClass < iClassMax; iClass++) {
			generateEnumDef(javaGnomeOutputDir, model, list.item(iClass),
					nameSpace, include);
		}
		model.build(new File(javaGnomeOutputDir + "/src/bindings"));

	}

	private static void generateEnumDef(String javaGnomeOutputDir,
			JCodeModel model, Node item, String nameSpace, String include)
			throws Exception {

		StringBuffer buff = new StringBuffer();
		String name = executeXPathString(item, "./@name");

		JDefinedClass cls = model
				._class(getPackageName(nameSpace) + "." + name);
		cls._extends(model.directClass("org.freedesktop.bindings.Constant"));

		JMethod constructorMethod = cls.constructor(JMod.PRIVATE);
		JVar ordinal = constructorMethod.param(model.INT, "ordinal");
		JVar nickname = constructorMethod.param(String.class, "nickname");
		JInvocation superInvocation = constructorMethod.body().invoke("super");
		superInvocation.arg(ordinal);
		superInvocation.arg(nickname);

		String plumbing = executeXPathString(item, "./@c:type");
		startSExp(buff, "define-enum", name);

		addSExp(buff, 2, "in-module", nameSpace);

		addSExp(buff, 2, "c-name", plumbing);

		buff.append("  (values").append(NL);
		NodeList members = executeXPathList(item, "./ns:member");
		for (int i = 0, iMax = members.getLength(); i < iMax; i++) {
			Node member = members.item(i);
			buff.append("    '(\"")
					.append(executeXPathString(member, "./@name"))
					.append("\" \"")
					.append(executeXPathString(member, "./@c:identifier"))
					.append("\")").append(NL);
		}
		buff.append("  )").append(NL);
		endSExp(buff);
		PrintWriter fos = new PrintWriter(javaGnomeOutputDir + "/src/defs/"
				+ nameSpace + "_" + name + ".defs");
		fos.write(buff.toString());
		fos.flush();
		fos.close();
	}

	private static void generatePlumbing(JCodeModel model, String nameSpace)
			throws Exception {
		int mod = JMod.PUBLIC | JMod.ABSTRACT;

		nameSpace = getPackageName(nameSpace);
		JDefinedClass cls = model._class(mod, nameSpace + ".Plumbing",
				ClassType.CLASS);

		cls._extends(model.directClass("org.gnome.gtk.Plumbing"));
	}

	private static String getPackageName(String nameSpace) {
		nameSpace = firstLetterLower(nameSpace);

		return "org.gnome." + nameSpace;
	}

	private static String firstLetterLower(String nameSpace) {
		nameSpace = Character.toLowerCase(nameSpace.charAt(0))
				+ (nameSpace.length() > 1 ? nameSpace.substring(1) : "");
		return nameSpace;
	}

	private static String executeXPathString(Node node, String string)
			throws Exception {
		Node result = (Node) executeXPath(node, string, XPathConstants.NODE);
		return (result != null) ? result.getTextContent() : null;
	}

	static final String NL = System.lineSeparator();

	private static void generateDef(String javaGnomeOutputDir,
			JCodeModel model, Node item, String nameSpace, String include)
			throws Exception {
		StringBuffer buff = new StringBuffer();

		String cname = executeXPathString(item, "./@name");

		String parent = executeXPathString(item, "./@parent");

		if (parent.equals("Gtk.Container")) {
			parent = "org.gnome.gtk.Container";
		} else if (parent.equals("GObject.Object")) {
			parent = "org.gnome.glib.Object";
		}
		System.out.println(cname + " extends " + parent);
		JDefinedClass cls = model._class(getPackageName(nameSpace) + "."
				+ cname);
		cls._extends(model.directClass(parent));

		startSExp(buff, "define-object", cname);

		addSExp(buff, 2, "in-module", nameSpace);
		addSExp(buff, 2, "import-header", include);
		addSExp(buff, 2, "parent", buildParentString(parent));
		String plumbing = executeXPathString(item, "./@c:type");
		addSExp(buff, 2, "c-name", plumbing);

		endSExp(buff);

		generateConstructors(buff, model, parent, nameSpace, cls, item, cname,
				plumbing);

		generateMethods(buff, model, parent, nameSpace, cls, item, plumbing);

		PrintWriter fos = new PrintWriter(javaGnomeOutputDir + "/src/defs/"
				+ nameSpace + "_" + cname + ".defs");
		fos.write(buff.toString());
		fos.flush();
		fos.close();

	}

	private static void generateMethods(StringBuffer buff, JCodeModel model,
			String parent, String nameSpace, JDefinedClass cls, Node item,
			String cname) throws Exception {
		// NodeList methods = executeXPathList(item,
		// "./ns:method[@name='load_uri']");
		NodeList methods = executeXPathList(item, "./ns:method");
		for (int i = 0, iMax = methods.getLength(); i < iMax; i++) {
			generateMethod(buff, model, parent, nameSpace, cls,
					methods.item(i), cname);
		}
	}

	private static void generateMethod(StringBuffer buff, JCodeModel model,
			String parent, String nameSpace, JDefinedClass cls, Node method,
			String cname) throws Exception {
		String methodName = executeXPathString(method, "./@name");
		startSExp(buff, "define-method", methodName);

		addSExp(buff, 2, "of-object", cname);
		String plumbing = executeXPathString(method, "./@c:identifier");
		addSExp(buff, 2, "c-name", plumbing);
		String type = executeXPathString(method,
				"./ns:return-value/ns:type/@c:type");
		String retType = buildParentString(type);
		if (retType.equals("void")) {
			retType = "none";
		}
		addSExp(buff, 2, "return-type", retType);

		// JClass jRetType = model.directClass(getJavaType(retType));
		// JMethod jm = cls.method(JMod.PUBLIC, jRetType, methodName);

		generateParameters(buff, method);
		endSExp(buff);

	}

	private static void generateParameters(StringBuffer buff, Node method)
			throws Exception {
		buff.append("  (parameters").append(NL);
		NodeList params = executeXPathList(method,
				"./ns:parameters/ns:parameter");
		for (int i = 0, iMax = params.getLength(); i < iMax; i++) {
			generateParameter(buff, params.item(i));
		}

		String throwsVal = executeXPathString(method, "./@throws");
		if (throwsVal != null && throwsVal.equals("1")) {
			buff.append("    '(\"").append("GError**").append('"');
			buff.append(" \"").append("error").append("\")").append(NL);
		}
		buff.append("  )").append(NL);
	}

	private static void generateParameter(StringBuffer buff, Node param)
			throws Exception {
		buff.append("    '(\"")
				.append(executeXPathString(param, "./ns:type/@c:type"))
				.append('"');
		buff.append(" \"").append(executeXPathString(param, "./@name"))
				.append("\")").append(NL);
	}

	private static void generateConstructors(StringBuffer buff,
			JCodeModel model, String parent, String nameSpace,
			JDefinedClass cls, Node item, String cname, String plumbing)
			throws Exception {
		NodeList list = executeXPathList(item, "./ns:constructor");

		for (int i = 0, iMax = list.getLength(); i < iMax; i++) {
			generateConstructor(buff, model, parent, nameSpace, cls,
					list.item(i), cname, plumbing);
		}
		JMethod constructorMethod = cls.constructor(JMod.PROTECTED);
		JVar pointer = constructorMethod.param(model.LONG, "pointer");
		JInvocation superInvocation = constructorMethod.body().invoke("super");
		superInvocation.arg(pointer);

	}

	private static void generateConstructor(StringBuffer buff,
			JCodeModel model, String parent, String nameSpace,
			JDefinedClass cls, Node constructor, String cname, String plumbing2)
			throws Exception {
		startSExp(buff, "define-function",
				cname + "_" + executeXPathString(constructor, "./@name"));

		addSExp(buff, 2, "is-constructor-of", plumbing2);
		String methodName = executeXPathString(constructor, "./@c:identifier");
		addSExp(buff, 2, "c-name", methodName);
		// addSExp(buff, 2, "caller-owns-return")
		addSExp(buff,
				2,
				"return-type",
				buildParentString(executeXPathString(constructor,
						"./ns:return-value/ns:type/@c:type")));

		generateParameters(buff, constructor);
		endSExp(buff);

		JMethod constructorMethod = cls.constructor(JMod.PUBLIC);
		JClass plumbing = model.directClass(getPackageName(nameSpace) + "."
				+ plumbing2);
		JInvocation staticInvoke = plumbing.staticInvoke("create"
				+ mapToJavaMethodName(methodName));
		NodeList params = executeXPathList(constructor,
				"./ns:parameters/ns:parameter");
		Node param;
		JVar jvar;
		for (int i = 0, iMax = params.getLength(); i < iMax; i++) {
			param = params.item(i);
			String paramName = executeXPathString(param, "./@name");
			String cType = getJavaType(
					executeXPathString(param, "./ns:type/@c:type"), nameSpace);

			jvar = constructorMethod.param(model.directClass(cType), paramName);
			staticInvoke.arg(jvar);
		}
		JInvocation superInvocation = constructorMethod.body().invoke("super");

		superInvocation.arg(staticInvoke);
	}

	private static String getJavaType(String type, String nameSpace) {
		if (type.equals("gchar*")) {
			return "java.lang.String";
		}
		if (type.equals("gssize")) {
			return "int";
		}
		type = type.replace(nameSpace, "");
		type = type.replace("*", "");
		return type;
	}

	private static String firstLetterUpper(String nameSpace) {
		nameSpace = Character.toUpperCase(nameSpace.charAt(0))
				+ (nameSpace.length() > 1 ? nameSpace.substring(1) : "");
		return nameSpace;
	}

	static String mapToJavaMethodName(String methodName) {
		methodName = methodName.replace("webkit_", "");
		methodName = methodName.replace("_new_", "_");
		methodName = methodName.replace("_new", "_");

		String[] arr = methodName.split("_");
		StringBuffer buff = new StringBuffer();
		for (String s : arr) {
			buff.append(firstLetterUpper(s));
		}
		return buff.toString();
	}

	private static String buildParentString(String name) {
		if (name == null)
			return name;
		int index = name.indexOf("Gtk.");
		if (index != -1) {
			return name.substring(index + "Gtk.".length());
		}
		return name;
	}

	private static void addSExp(StringBuffer buff, int indent, String key,
			String value) {
		for (int i = 0; i < indent; i++) {
			buff.append(" ");
		}
		buff.append("(").append(key).append(" \"").append(value).append("\")")
				.append(NL);
	}

	private static void endSExp(StringBuffer buff) {
		buff.append(")").append(NL).append(NL);

	}

	private static void startSExp(StringBuffer buff, String key, String value) {
		buff.append("(").append(key).append(" ").append(value).append(NL);

	}

	private static NodeList executeXPathList(Node doc, String string)
			throws Exception {
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

	private static Object executeXPath(Node doc, String string, QName retType)
			throws XPathExpressionException {
		XPathExpression expr = expressionMap.get(string);
		if (expr == null) {
			expr = xpath.compile(string);
			expressionMap.put(string, expr);
		}
		return expr.evaluate(doc, retType);
	}

	private static class GirNameSpaceContext implements NamespaceContext {

		public String getNamespaceURI(String prefix) {
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

		public String getPrefix(String namespaceURI) {
			return null;
		}

		public Iterator getPrefixes(String namespaceURI) {
			return null;
		}

	}

}
