/*
	Copyright 2003 by Marc J. Rochkind. All rights reserved.
	May be copied only for purposes and under conditions described
	on the Web page www.basepath.com/aup/copyright.htm.

	The Example Files are provided "as is," without any warranty;
	without even the implied warranty of merchantability or fitness
	for a particular purpose. The author and his publisher are not
	responsible for any damages, direct or incidental, resulting
	from the use or non-use of these Example Files.

	The Example Files may contain defects, and some contain deliberate
	coding mistakes that were included for educational reasons.
	You are responsible for determining if and how the Example Files
	are to be used.

*/
#define __EXTENSIONS__
#include "defs.h"
#include <netdb.h>
#include <sys/msg.h>
#include <sys/sem.h>
#include <sys/shm.h>
#include <mqueue.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/poll.h>
#include <sys/resource.h>
#include <netinet/in.h>
#include "jtux_util.h"
#include "JNI_macros.h"

/*
	Following comes from "The Native Java Interface" (Sheng Liang; Addison-Wesley), p. 75.
*/
void JNU_ThrowByName(JNIEnv *env, const char *name, const char *msg)
{
	jclass cls = (*env)->FindClass(env, name);
	/* if NULL, exception has already been thrown */
	// Really want to use Throw so constructor with errno can be used.
	if (cls != NULL)
		(*env)->ThrowNew(env, cls, msg);
	(*env)->DeleteLocalRef(env, cls);
}

bool setup_throw_errno(JNIEnv *env, int code)
{
	jclass cls = (*env)->FindClass(env, "jtux/UErrorException");
	if (cls != NULL) {
		jmethodID mid = (*env)->GetMethodID(env, cls, "<init>", "(I)V");
		if (mid != NULL) {
			jobject obj = (*env)->NewObject(env, cls, mid, code);
			if (obj != NULL)
				(*env)->Throw(env, obj);
		}
	}
	//char buf[200];

	//(void)syserrmsgline(buf, sizeof(buf), code, EC_ERRNO);
	//JNU_ThrowByName(env, "jtux/JtuxErrorException", buf);
	return true;
}

bool setup_throw_errno_type(JNIEnv *env, int code, int type)
{
	jclass cls = (*env)->FindClass(env, "jtux/UErrorException");
	if (cls != NULL) {
		jmethodID mid = (*env)->GetMethodID(env, cls, "<init>", "(II)V");
		if (mid != NULL) {
			jobject obj = (*env)->NewObject(env, cls, mid, code, type);
			if (obj != NULL)
				(*env)->Throw(env, obj);
		}
	}
	return true;
}

JNIEXPORT jstring JNICALL Java_jtux_UUtil_strerror(JNIEnv *env, jclass obj,
  jint errnum)
{
	JSTR_RETURN(strerror(errnum));
}

JNIEXPORT void JNICALL Java_jtux_UUtil_check_1type_1sizes(JNIEnv *env, jclass obj)
{
	bool ok = true;
	/*
		All assumptions about suitability of Java primitive types for POSIX/SUS types
		must be tested here.
	*/
	if (sizeof(jlong) < sizeof(long)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(long)\n");
	}
	if (sizeof(jlong) < sizeof(void *)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(void *)\n");
	}
	if (sizeof(jint) < sizeof(int)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(int)\n");
	}
	if (sizeof(jshort) < sizeof(short)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jshort) < sizeof(short)\n");
	}
	if (sizeof(jlong) < sizeof(clock_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(clock_t)\n");
	}
	if (sizeof(jlong) < sizeof(gid_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(gid_t)\n");
	}
	if (sizeof(jlong) < sizeof(pid_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(pid_t)\n");
	}
	if (sizeof(jlong) < sizeof(uid_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(uid_t)\n");
	}
	if (sizeof(jlong) < sizeof(rlim_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(rlim_t)\n");
	}
	if (sizeof(jlong) < sizeof(time_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(time_t)\n");
	}
	if (sizeof(jlong) < sizeof(suseconds_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(suseconds_t)\n");
	}
	if (sizeof(jlong) < sizeof(useconds_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(useconds_t)\n");
	}
	if (sizeof(jlong) < sizeof(off_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(off_t)\n");
	}
	if (sizeof(jlong) < sizeof(fsfilcnt_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(fsfilcnt_t)\n");
	}
	if (sizeof(jlong) < sizeof(fsblkcnt_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(fsblkcnt_t)\n");
	}
	if (sizeof(jint) < sizeof(size_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(size_t)\n");
	}
	if (sizeof(jint) < sizeof(ssize_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(ssize_t)\n");
	}
	if (sizeof(jint) < sizeof(mode_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(mode_t)\n");
	}
	if (sizeof(jlong) < sizeof(dev_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(dev_t)\n");
	}
	if (sizeof(jint) < sizeof(ino_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(ino_t)\n");
	}
	if (sizeof(jint) < sizeof(nlink_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(nlink_t)\n");
	}
	if (sizeof(jint) < sizeof(blksize_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(blksize_t)\n");
	}
	if (sizeof(jlong) < sizeof(blkcnt_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(blkcnt_t)\n");
	}
	if (sizeof(jint) < sizeof(nfds_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(nfds_t)\n");
	}
	if (sizeof(jlong) < sizeof(key_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(key_t)\n");
	}
	if (sizeof(jint) < sizeof(msgqnum_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(msgqnum_t)\n");
	}
	if (sizeof(jint) < sizeof(msglen_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(msglen_t)\n");
	}
	if (sizeof(jlong) < sizeof(mqd_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jlong) < sizeof(mqd_t)\n");
	}
	if (sizeof(jint) < sizeof(socklen_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(socklen_t)\n");
	}
	if (sizeof(jint) < sizeof(sa_family_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(sa_family_t)\n");
	}
	if (sizeof(jshort) < sizeof(in_port_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jshort) < sizeof(in_port_t)\n");
	}
	if (sizeof(jint) < sizeof(in_addr_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(jint) < sizeof(in_addr_t)\n");
	}
	/*
		Assuming C ints (not only jints) are at least 32-bits, because of the int
		argument to field_ctoj_int.
	*/
	if (sizeof(int) < sizeof(in_addr_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(int) < sizeof(in_addr_t)\n");
	}
	if (sizeof(int) < sizeof(ino_t)) {
		ok = false;
		fprintf(stderr, "Type size error: sizeof(int) < sizeof(ino_t)\n");
	}
	if (!ok)
		setup_throw_errno(env, ENOSYS);
}

/*
	Very crude implementation, under the assumption that speed isn't important. Can
	be improved in space and time.
*/
/* Following from macrostr.c, but our lookup is different. */
static struct {
	char *ms_cat;
	intptr_t ms_code;
	char *ms_macro;
	char *ms_desc;
} macrostr_db[] = {
#include "include/macrostr.incl"
	{ NULL, 0, NULL, NULL}
};

JNIEXPORT jlong JNICALL Java_jtux_UUtil_GetSymbol(JNIEnv *env, jclass obj,
  jstring category, jstring symbol)
{
	JSTR_GET_DECL(category_c, category)
	JSTR_GET_DECL(symbol_c, symbol)
	bool found = false;
	long val;
	int i;

	JSTR_NULLTEST_V(category_c, -1)
	JSTR_NULLTEST_V(symbol_c, -1)
	for (i = 0; macrostr_db[i].ms_cat != NULL; i++)
		if (strcmp(macrostr_db[i].ms_cat, category_c) == 0 &&
		  strcmp(macrostr_db[i].ms_macro, symbol_c) == 0) {
			val = macrostr_db[i].ms_code;
			found = true;
			break;
		}
	JSTR_REL(category_c, category)
	JSTR_REL(symbol_c, symbol)
	if (!found)
		return 0;
	return val;
}

JNIEXPORT jstring JNICALL Java_jtux_UUtil_GetSymbolStr(JNIEnv *env, jclass obj, jstring category,
  jint code)
{
	JSTR_GET_DECL(category_c, category)
	int i;
	char *s = NULL;

	JSTR_NULLTEST_V(category_c, NULL)
	for (i = 0; macrostr_db[i].ms_cat != NULL; i++)
		if (strcmp(macrostr_db[i].ms_cat, category_c) == 0 &&
		  macrostr_db[i].ms_code == code) {
			s = macrostr_db[i].ms_macro;
			break;
		}
	JSTR_REL(category_c, category)
	JSTR_RETURN(s);
}

/*
	Following functions taking a jclass check it against NULL, so OK for
	argument to be:

		(*env)->FindClass(env, classname)

	if the overhead of calling FindClass each time is acceptable.
*/

bool field_ctoj_long(JNIEnv *env, jclass cls, const char *field, jobject obj, long n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "J")) == NULL)
		return false;
	(*env)->SetLongField(env, obj, fid, n);
	return true;
}

bool field_ctoj_int(JNIEnv *env, jclass cls, const char *field, jobject obj, int n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "I")) == NULL)
		return false;
	(*env)->SetIntField(env, obj, fid, n);
	return true;
}

bool field_ctoj_boolean(JNIEnv *env, jclass cls, const char *field, jobject obj, int n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "Z")) == NULL)
		return false;
	(*env)->SetBooleanField(env, obj, fid, n);
	return true;
}

bool field_ctoj_short(JNIEnv *env, jclass cls, const char *field, jobject obj, short n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "S")) == NULL)
		return false;
	(*env)->SetShortField(env, obj, fid, n);
	return true;
}

bool field_ctoj_object(JNIEnv *env, jclass cls, const char *field, const char *sig, jobject obj,
  jobject fobj)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, sig)) == NULL)
		return false;
	(*env)->SetObjectField(env, obj, fid, fobj);
	return true;
}

bool field_ctoj_string(JNIEnv *env, jclass cls, const char *field, jobject obj,
  const char *s)
{
	jfieldID fid;
	jstring js;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "Ljava/lang/String;")) == NULL)
		return false;
	if ((js = (*env)->NewStringUTF(env, s == NULL ? "" : s)) == NULL)
		return false;
	(*env)->SetObjectField(env, obj, fid, js);
	return true;
}

bool field_jtoc_long(JNIEnv *env, jclass cls, const char *field, jobject obj, long *n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "J")) == NULL)
		return false;
	*n = (*env)->GetLongField(env, obj, fid);
	return true;
}

bool field_jtoc_int(JNIEnv *env, jclass cls, const char *field, jobject obj, int *n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "I")) == NULL)
		return false;
	*n = (*env)->GetIntField(env, obj, fid);
	return true;
}

bool field_jtoc_boolean(JNIEnv *env, jclass cls, const char *field, jobject obj, int *n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "Z")) == NULL)
		return false;
	*n = (*env)->GetBooleanField(env, obj, fid);
	return true;
}

bool field_jtoc_short(JNIEnv *env, jclass cls, const char *field, jobject obj, short *n)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "S")) == NULL)
		return false;
	*n = (*env)->GetShortField(env, obj, fid);
	return true;
}

bool field_jtoc_bytearray(JNIEnv *env, jclass cls, const char *field, jobject obj,
  void **ptr, jbyteArray *ba)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "[B")) == NULL)
		return false;
	if ((*ba = (*env)->GetObjectField(env, obj, fid)) == NULL)
		return false;
	*ptr = (*env)->GetByteArrayElements(env, *ba, NULL);
	return true;
}

void field_jtoc_bytearray_release(JNIEnv *env, jbyteArray ba, void *p)
{
	if (ba != NULL && p != NULL)
		(*env)->ReleaseByteArrayElements(env, ba, p, 0);
}

void field_jtoc_bytearray_release_nocopy(JNIEnv *env, jbyteArray ba, void *p)
{
	if (ba != NULL && p != NULL)
		(*env)->ReleaseByteArrayElements(env, ba, p, JNI_ABORT);
}

bool field_jtoc_object(JNIEnv *env, jclass cls, const char *field, const char *sig,
  jobject obj, jobject *fobj)
{
	jfieldID fid;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, sig)) == NULL)
		return false;
	*fobj = (*env)->GetObjectField(env, obj, fid);
	return true;
}

bool field_jtoc_string(JNIEnv *env, jclass cls, const char *field, jobject obj,
  char *buf, size_t bufsize)
{
	jfieldID fid;
	jstring str;
	const jbyte *str_c;

	if (cls == NULL)
		return false;
	if ((fid = (*env)->GetFieldID(env, cls, field, "Ljava/lang/String;")) == NULL)
		return false;
	if ((str = (*env)->GetObjectField(env, obj, fid)) == NULL)
		return false;
	if ((str_c = (*env)->GetStringUTFChars(env, str, NULL)) == NULL)
		return false;
	strncpy(buf, str_c, bufsize - 1);
	buf[bufsize - 1] = '\0';
	(*env)->ReleaseStringUTFChars(env, str, str_c);
	return true;
}

/*
	Assumption is that next three functions are seldom used, so no attempt to
	cache class or method ID.
*/

bool string_buffer_set(JNIEnv *env, jobject sb, const char *s)
{
	jclass Utilclass = (*env)->FindClass(env, "jtux/UUtil");
	jmethodID mid;

	if (Utilclass == NULL)
		return false;
	if ((mid = (*env)->GetStaticMethodID(env, Utilclass, "StringBufferSet",
	  "(Ljava/lang/StringBuffer;Ljava/lang/String;)V")) == NULL)
		return false;
	(*env)->CallStaticVoidMethod(env, Utilclass, mid, sb, (*env)->NewStringUTF(env, s));
	return (*env)->ExceptionCheck(env) == 0;
}

/*
	Caller should not release if returns NULL.
*/
const char *string_buffer_get(JNIEnv *env, jobject sb, jstring *obj_str)
{
	jclass Utilclass = (*env)->FindClass(env, "jtux/UUtil");
	jmethodID mid;

	if (Utilclass == NULL)
		return NULL;
	if ((mid = (*env)->GetStaticMethodID(env, Utilclass, "StringBufferGet",
	  "(Ljava/lang/StringBuffer;)Ljava/lang/String;")) == NULL)
		return NULL;
	*obj_str = (*env)->CallStaticObjectMethod(env, Utilclass, mid, sb);
	if ((*env)->ExceptionCheck(env))
		return NULL; // caller should not release
	return (*env)->GetStringUTFChars(env, *obj_str, NULL);
}

void string_buffer_release(JNIEnv *env, jstring obj_string, const char *s)
{
	(*env)->ReleaseStringUTFChars(env, obj_string, s);
}

jbyte *get_sigset(JNIEnv *env, jobject obj, jbyteArray *ba)
{
	jclass cls = (*env)->FindClass(env, "jtux/UProcess$sigset_t");
	jfieldID fid;

	if (cls == NULL || obj == NULL)
		return NULL;
	if ((fid = (*env)->GetFieldID(env, cls, "set", "[B")) == NULL)
		return NULL;
	if ((*ba = (*env)->GetObjectField(env, obj, fid)) == NULL)
		return NULL;
	return (*env)->GetByteArrayElements(env, *ba, NULL);
}

void release_sigset(JNIEnv *env, jbyteArray ba, jbyte *p)
{
	// Always copy back, even though for sigismember we don't have to
	if (ba != NULL && p != NULL)
		(*env)->ReleaseByteArrayElements(env, ba, p, 0);
}

JNIEXPORT void JNICALL Java_jtux_UUtil_jaddr_1to_1seg(JNIEnv *env, jclass obj,
  jlong addr, jbyteArray data, jint datasize)
{
	void *p;

	if ((p = (*env)->GetByteArrayElements(env, data, NULL)) == NULL)
		return;
	memcpy((void *)(intptr_t)addr, p, datasize);
	(*env)->ReleaseByteArrayElements(env, data, p, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_jtux_UUtil_jaddr_1from_1seg(JNIEnv *env, jclass obj,
  jlong addr, jbyteArray data, jint datasize)
{
	void *p;

	if ((p = (*env)->GetByteArrayElements(env, data, NULL)) == NULL)
		return;
	memcpy(p, (void *)(intptr_t)addr, datasize);
	(*env)->ReleaseByteArrayElements(env, data, p, 0);
}

bool get_IntHolder_int(JNIEnv *env, jobject obj_ih, int *v)
{
	return field_jtoc_int(env, (*env)->FindClass(env, "jtux/UUtil$IntHolder"),
	  "value", obj_ih, v);
}

bool set_IntHolder_int(JNIEnv *env, jobject obj_ih, int v)
{
	return field_ctoj_int(env, (*env)->FindClass(env, "jtux/UUtil$IntHolder"),
	  "value", obj_ih, v);
}

struct iovec *iovec_jtoc(JNIEnv *env, jobject iov, int iovcnt, jbyteArray **ba)
{
	struct iovec *v;
	int i;
	jclass cls = (*env)->FindClass(env, "jtux/UFile$s_iovec");

	JTHROW_null(v = malloc(iovcnt * sizeof(struct iovec)))
	if (v == NULL)
		return NULL;
	JTHROW_null(*ba = malloc(iovcnt * sizeof(jbyteArray)))
	if (*ba == NULL) {
		free(v);
		return NULL;
	}
	for (i = 0; i < iovcnt; i++) {
		jobject v_obj = (*env)->GetObjectArrayElement(env, iov, i);

		if (v_obj == NULL) {
			free(v);
			free(*ba);
			return NULL;
		}
		if (!field_jtoc_bytearray(env, cls, "iov_base", v_obj, &v[i].iov_base,
		  &(*ba)[i])) {
			free(v);
			free(*ba);
			return NULL;
		}
		if (!field_jtoc_int(env, cls, "iov_len", v_obj, &v[i].iov_len)) {
			free(v);
			free(*ba);
			return NULL;
		}
	}
	return v;
}

void iovec_jtoc_release_nocopy(JNIEnv *env, struct iovec *v, int iovcnt, jbyteArray *ba)
{
	int i;

	for (i = 0; i < iovcnt; i++)
		field_jtoc_bytearray_release_nocopy(env, ba[i], v[i].iov_base);
	free(v);
	free(ba);
}

void iovec_jtoc_release(JNIEnv *env, struct iovec *v, int iovcnt, jbyteArray *ba)
{
	int i;

	for (i = 0; i < iovcnt; i++)
		field_jtoc_bytearray_release(env, ba[i], v[i].iov_base);
	free(v);
	free(ba);
}
