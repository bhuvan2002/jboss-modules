/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.modules;

import __redirected.__JAXPRedirected;
import org.jboss.modules.log.JDKModuleLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.jar.JarFile;
import java.util.logging.LogManager;

/**
 * The main entry point of JBoss Modules when run as a JAR on the command line.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jason T. Greene
 * @apiviz.exclude
 */
public final class Main {


    static {
        // Force initialization at the earliest possible point
        @SuppressWarnings("unused")
        long start = StartTimeHolder.START_TIME;
    }

    private Main() {
    }

    /**
     * Get the name of the JBoss Modules JAR.
     *
     * @return the name
     */
    public static String getJarName() {
        return "UNSET";
    }

    /**
     * Get the version string of JBoss Modules.
     *
     * @return the version string
     */
    public static String getVersionString() {
        return "TRUNK SNAPSHOT";
    }

    private static void usage() {
        System.out.println("Usage: java [-jvmoptions...] -jar " + getJarName() + ".jar [-options...] <module-spec> [args...]\n");
        System.out.println("       java [-jvmoptions...] -jar " + getJarName() + ".jar [-options...] -jar <jar-name> [args...]\n");
        System.out.println("where options include:");
        System.out.println("    -help         Display this message");
        System.out.println("    -modulepath <search path of directories>");
        System.out.println("    -mp <search path of directories>");
        System.out.println("                  A list of directories, separated by '" + File.pathSeparator + "', where modules may be located");
        System.out.println("                  If not specified, the value of the \"module.path\" system property is used");
        System.out.println("    -class <main-class>");
        System.out.println("                  The main class.");
        System.out.println("    -cp,-classpath <classpath>");
        System.out.println("                  The classpath to use for the module.");
        System.out.println("    -dep,-dependencies <dependencies>");
        System.out.println("                  The dependencies required for the module.");
        System.out.println("    -config <config-location>");
        System.out.println("                  The location of the module configuration.  Either -mp or -config");
        System.out.println("                  may be specified, but not both");
        System.out.println("    -logmodule <module-name>");
        System.out.println("                  The module to use to load the system logmanager");
        System.out.println("    -jaxpmodule <module-name>");
        System.out.println("                  The default JAXP implementation to use of the JDK.");
        System.out.println("    -version      Print version and exit\n");
        System.out.println("and module-spec is a valid module specification string");
    }

    /**
     * Run JBoss Modules.
     *
     * @param args the command-line arguments
     *
     * @throws Throwable if an error occurs
     */
    public static void main(String[] args) throws Throwable {
        final int argsLen = args.length;
        String deps = null;
        String[] moduleArgs = null;
        String modulePath = null;
        String configPath = null;
        String classpath = null;
        String cpArgUsed = null;
        String depArgUsed = null;
        boolean jar = false;
        boolean classpathDefined = false;
        boolean classDefined = false;
        String moduleIdentifierOrExeName = null;
        ModuleIdentifier logManagerModuleIdentifier = null;
        ModuleIdentifier jaxpModuleIdentifier = null;
        for (int i = 0, argsLength = argsLen; i < argsLength; i++) {
            final String arg = args[i];
            try {
                if (arg.charAt(0) == '-') {
                    // it's an option
                    if ("-version".equals(arg)) {
                        System.out.println("Module loader " + getVersionString());
                        return;
                    } else if ("-help".equals(arg)) {
                        usage();
                        return;
                    } else if ("-modulepath".equals(arg) || "-mp".equals(arg)) {
                        if (modulePath != null) {
                            System.err.println("Module path may only be specified once");
                            System.exit(1);
                        }
                        if (configPath != null) {
                            System.err.println("Module path may not be specified with config path");
                            System.exit(1);
                        }
                        modulePath = args[++i];
                        System.setProperty("module.path", modulePath);
                    } else if ("-config".equals(arg)) {
                        if (configPath != null) {
                            System.err.println("Config file path may only be specified once");
                            System.exit(1);
                        }
                        if (modulePath != null) {
                            System.err.println("Module path may not be specified with config path");
                            System.exit(1);
                        }
                        configPath = args[++i];
                    } else if ("-logmodule".equals(arg)) {
                        logManagerModuleIdentifier = ModuleIdentifier.fromString(args[++i]);
                    } else if ("-jaxpmodule".equals(arg)) {
                        jaxpModuleIdentifier = ModuleIdentifier.fromString(args[++i]);
                    } else if ("-jar".equals(arg)) {
                        if (jar) {
                            System.err.println("-jar flag may only be specified once");
                            System.exit(1);
                        }
                        jar = true;
                    } else if ("-cp".equals(arg) || "-classpath".equals(arg)) {
                        if (classpathDefined) {
                            showErrorAndExit(false, "-cp or -classpath may only be specified once.");
                        }
                        classpathDefined = true;
                        cpArgUsed = arg;
                        classpath = args[++i];
                        AccessController.doPrivileged(new PropertyWriteAction("java.class.path", classpath));
                    } else if ("-dep".equals(arg) || "-dependencies".equals(arg)) {
                        if (deps != null) {
                            showErrorAndExit(false, "-dep or -dependencies may only be specified once.");
                        }
                        depArgUsed = arg;
                        deps = args[++i];
                    } else if ("-class".equals(arg)) {
                        // TODO - We are working here
                        classDefined = true;
                        moduleIdentifierOrExeName = args[++i];
                    } else {
                        System.err.printf("Invalid option '%s'\n", arg);
                        usage();
                        System.exit(1);
                    }
                } else {
                    // Check to see if already defined with -class
                    if (!classDefined) {
                        // it's the module specification
                        moduleIdentifierOrExeName = arg;
                    }
                    int cnt = argsLen - i - 1;
                    moduleArgs = new String[cnt];
                    System.arraycopy(args, i + 1, moduleArgs, 0, cnt);
                    break;
                }
            } catch (IndexOutOfBoundsException e) {
                System.err.printf("Argument expected for option %s\n", arg);
                usage();
                System.exit(1);
            }
        }

        // Final argument check
        if (jar && classpathDefined) {
            showErrorAndExit(true, "Both arguments -jar and %s cannot be specified.%n", cpArgUsed);
        } else if (!classpathDefined && deps != null) {
            showErrorAndExit(true, "Argument %s is not allowed if -cp or -classpath was not used.%n", depArgUsed);
        } else if (classpathDefined && classDefined) {
            showErrorAndExit(true, "Argument %s not allowed when -class has been specified.%n", cpArgUsed);
        }

        // run the module
        if (moduleIdentifierOrExeName == null) {
            System.err.println("No module or JAR specified");
            usage();
            System.exit(1);
        }
        final ModuleLoader loader;
        final ModuleIdentifier moduleIdentifier;
        if (jar) {
            loader = new JarModuleLoader(DefaultBootModuleLoaderHolder.INSTANCE, new JarFile(moduleIdentifierOrExeName));
            moduleIdentifier = ((JarModuleLoader) loader).getMyIdentifier();
        } else if (configPath != null) {
            loader = ModuleXmlParser.parseModuleConfigXml(new File(configPath));
            moduleIdentifier = ModuleIdentifier.fromString(moduleIdentifierOrExeName);
        } else if (classpathDefined || classDefined) {
            loader = new ClassPathModuleLoader(moduleIdentifierOrExeName, classpath, deps);
            moduleIdentifier = ClassPathModuleLoader.IDENTIFIER;
        } else {
            loader = DefaultBootModuleLoaderHolder.INSTANCE;
            moduleIdentifier = ModuleIdentifier.fromString(moduleIdentifierOrExeName);
        }
        Module.initBootModuleLoader(loader);
        if (logManagerModuleIdentifier != null) {
            final ModuleClassLoader classLoader = loader.loadModule(logManagerModuleIdentifier).getClassLoaderPrivate();
            final InputStream stream = classLoader.getResourceAsStream("META-INF/services/java.util.logging.LogManager");
            if (stream != null) {
                try {
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    String name = null;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final int i = line.indexOf('#');
                        if (i != -1) {
                            line = line.substring(0, i);
                        }
                        line = line.trim();
                        if (line.length() == 0) continue;
                        name = line;
                        break;
                    }
                    if (name != null) {
                        System.setProperty("java.util.logging.manager", name);
                        final ClassLoader old = setContextClassLoader(classLoader);
                        try {
                            if (LogManager.getLogManager().getClass() == LogManager.class) {
                                System.err.println("WARNING: Failed to load the specified logmodule " + logManagerModuleIdentifier);
                            } else {
                                Module.setModuleLogger(new JDKModuleLogger());
                            }
                        } finally {
                            setContextClassLoader(old);
                        }
                    } else {
                        System.err.println("WARNING: No log manager services defined in specified logmodule " + logManagerModuleIdentifier);
                    }
                } finally {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            } else {
                System.err.println("WARNING: No log manager service descriptor found in specified logmodule " + logManagerModuleIdentifier);
            }
        }
        if (jaxpModuleIdentifier != null)
            __JAXPRedirected.changeAll(jaxpModuleIdentifier, Module.getBootModuleLoader());

        final Module module;
        try {
            module = loader.loadModule(moduleIdentifier);
        } catch (ModuleNotFoundException e) {
            e.printStackTrace(System.err);
            System.exit(1);
            return;
        }
        try {
            ModuleLoader.installMBeanServer();
            module.run(moduleArgs);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return;
    }

    private static ClassLoader setContextClassLoader(final ClassLoader classLoader) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return doSetContextClassLoader(classLoader);
                }
            });
        }
        return doSetContextClassLoader(classLoader);
    }

    private static ClassLoader doSetContextClassLoader(final ClassLoader classLoader) {
        try {
            return Thread.currentThread().getContextClassLoader();
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    private static void showErrorAndExit(final boolean showUsage, final String message) {
        System.err.println(message);
        if (showUsage) {
            usage();
        }
        System.exit(1);

    }

    private static void showErrorAndExit(final boolean showUsage, final String format, final Object... args) {
        System.err.printf(format, args);
        if (showUsage) {
            usage();
        }
        System.exit(1);

    }
}