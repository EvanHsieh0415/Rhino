/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package dev.latvian.mods.rhino;

import dev.latvian.mods.rhino.classfile.ByteCode;
import dev.latvian.mods.rhino.classfile.ClassFileWriter;

import java.lang.ref.SoftReference;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A security controller relying on Java {@link Policy} in effect. When you use
 * this security controller, your securityDomain objects must be instances of
 * {@link CodeSource} representing the location from where you load your
 * scripts. Any Java policy "grant" statements matching the URL and certificate
 * in code sources will apply to the scripts. If you specify any certificates
 * within your {@link CodeSource} objects, it is your responsibility to verify
 * (or not) that the script source files are signed in whatever
 * implementation-specific way you're using.
 *
 * @author Attila Szegedi
 */
public class PolicySecurityController extends SecurityController {
	private static final byte[] secureCallerImplBytecode = loadBytecode();

	// We're storing a CodeSource -> (ClassLoader -> SecureRenderer), since we
	// need to have one renderer per class loader. We're using weak hash maps
	// and soft references all the way, since we don't want to interfere with
	// cleanup of either CodeSource or ClassLoader objects.
	private static final Map<CodeSource, Map<ClassLoader, SoftReference<SecureCaller>>> callers = new WeakHashMap<>();

	@Override
	public Class<?> getStaticSecurityDomainClassInternal() {
		return CodeSource.class;
	}

	private static class Loader extends SecureClassLoader implements GeneratedClassLoader {
		private final CodeSource codeSource;

		Loader(ClassLoader parent, CodeSource codeSource) {
			super(parent);
			this.codeSource = codeSource;
		}

		@Override
		public Class<?> defineClass(String name, byte[] data) {
			return defineClass(name, data, 0, data.length, codeSource);
		}

		@Override
		public void linkClass(Class<?> cl) {
			resolveClass(cl);
		}
	}

	@Override
	public GeneratedClassLoader createClassLoader(final ClassLoader parent, final Object securityDomain) {
		return (Loader) AccessController.doPrivileged((PrivilegedAction<Object>) () -> new Loader(parent, (CodeSource) securityDomain));
	}

	@Override
	public Object getDynamicSecurityDomain(Object securityDomain) {
		// No separate notion of dynamic security domain - just return what was
		// passed in.
		return securityDomain;
	}

	@Override
	public Object callWithDomain(final Object securityDomain, final Context cx, Callable callable, Scriptable scope, Scriptable thisObj, Object[] args) {
		// Run in doPrivileged as we might be checked for "getClassLoader"
		// runtime permission
		final ClassLoader classLoader = (ClassLoader) AccessController.doPrivileged((PrivilegedAction<Object>) () -> cx.getApplicationClassLoader());
		final CodeSource codeSource = (CodeSource) securityDomain;
		Map<ClassLoader, SoftReference<SecureCaller>> classLoaderMap;
		synchronized (callers) {
			classLoaderMap = callers.get(codeSource);
			if (classLoaderMap == null) {
				classLoaderMap = new WeakHashMap<>();
				callers.put(codeSource, classLoaderMap);
			}
		}
		SecureCaller caller;
		synchronized (classLoaderMap) {
			SoftReference<SecureCaller> ref = classLoaderMap.get(classLoader);
			if (ref != null) {
				caller = ref.get();
			} else {
				caller = null;
			}
			if (caller == null) {
				try {
					// Run in doPrivileged as we'll be checked for
					// "createClassLoader" runtime permission
					caller = (SecureCaller) AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						Loader loader = new Loader(classLoader, codeSource);
						Class<?> c = loader.defineClass(SecureCaller.class.getName() + "Impl", secureCallerImplBytecode);
						return c.newInstance();
					});
					classLoaderMap.put(classLoader, new SoftReference<>(caller));
				} catch (PrivilegedActionException ex) {
					throw new UndeclaredThrowableException(ex.getCause());
				}
			}
		}
		return caller.call(callable, cx, scope, thisObj, args);
	}

	public abstract static class SecureCaller {
		public abstract Object call(Callable callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args);
	}


	private static byte[] loadBytecode() {
		String secureCallerClassName = SecureCaller.class.getName();
		ClassFileWriter cfw = new ClassFileWriter(secureCallerClassName + "Impl", secureCallerClassName, "<generated>");
		cfw.startMethod("<init>", "()V", ClassFileWriter.ACC_PUBLIC);
		cfw.addALoad(0);
		cfw.addInvoke(ByteCode.INVOKESPECIAL, secureCallerClassName, "<init>", "()V");
		cfw.add(ByteCode.RETURN);
		cfw.stopMethod((short) 1);
		String callableCallSig = "Ldev/latvian/mods/rhino/Context;" + "Ldev/latvian/mods/rhino/Scriptable;" + "Ldev/latvian/mods/rhino/Scriptable;" + "[Ljava/lang/Object;)Ljava/lang/Object;";

		cfw.startMethod("call", "(Ldev/latvian/mods/rhino/Callable;" + callableCallSig, (short) (ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_FINAL));
		for (int i = 1; i < 6; ++i) {
			cfw.addALoad(i);
		}
		cfw.addInvoke(ByteCode.INVOKEINTERFACE, "dev/latvian/mods/rhino/Callable", "call", "(" + callableCallSig);
		cfw.add(ByteCode.ARETURN);
		cfw.stopMethod((short) 6);
		return cfw.toByteArray();
	}
}