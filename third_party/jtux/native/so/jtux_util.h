#ifndef _JTUX_UTIL_H_
#define _JTUX_UTIL_H_

#include <jni.h>

void JNU_ThrowByName(JNIEnv *env, const char *name, const char *msg);
bool setup_throw_errno(JNIEnv *env, int code);
bool setup_throw_errno_type(JNIEnv *env, int code, int type);

bool field_ctoj_long(JNIEnv *env, jclass cls, const char *field, jobject obj, long n);
bool field_ctoj_int(JNIEnv *env, jclass cls, const char *field, jobject obj, int n);
bool field_ctoj_boolean(JNIEnv *env, jclass cls, const char *field, jobject obj, int n);
bool field_ctoj_short(JNIEnv *env, jclass cls, const char *field, jobject obj, short n);
bool field_ctoj_object(JNIEnv *env, jclass cls, const char *field, const char *sig, jobject obj,
  jobject fobj);
bool field_ctoj_string(JNIEnv *env, jclass cls, const char *field, jobject obj,
  const char *s);
bool field_jtoc_long(JNIEnv *env, jclass cls, const char *field, jobject obj, long *n);
bool field_jtoc_int(JNIEnv *env, jclass cls, const char *field, jobject obj, int *n);
bool field_jtoc_boolean(JNIEnv *env, jclass cls, const char *field, jobject obj, int *n);
bool field_jtoc_short(JNIEnv *env, jclass cls, const char *field, jobject obj, short *n);
bool field_jtoc_bytearray(JNIEnv *env, jclass cls, const char *field, jobject obj,
  void **ptr, jbyteArray *ba);
void field_jtoc_bytearray_release_nocopy(JNIEnv *env, jbyteArray ba, void *p);
void field_jtoc_bytearray_release(JNIEnv *env, jbyteArray ba, void *p);
bool field_jtoc_object(JNIEnv *env, jclass cls, const char *field, const char *sig,
  jobject obj, jobject *fobj);
bool field_jtoc_string(JNIEnv *env, jclass cls, const char *field, jobject obj,
  char *buf, size_t bufsize);
bool string_buffer_set(JNIEnv *env, jobject sb, const char *s);
const char *string_buffer_get(JNIEnv *env, jobject sb, jstring *obj_str);
void string_buffer_release(JNIEnv *env, jstring obj_string, const char *s);

jbyte *get_sigset(JNIEnv *env, jobject obj, jbyteArray *ba);
void release_sigset(JNIEnv *env, jbyteArray ba, jbyte *p);

bool get_IntHolder_int(JNIEnv *env, jobject obj_ih, int *v);
bool set_IntHolder_int(JNIEnv *env, jobject obj_ih, int v);

struct iovec *iovec_jtoc(JNIEnv *env, jobject iov, int iovcnt, jbyteArray **ba);
void iovec_jtoc_release_nocopy(JNIEnv *env, struct iovec *v, int iovcnt, jbyteArray *ba);
void iovec_jtoc_release(JNIEnv *env, struct iovec *v, int iovcnt, jbyteArray *ba);

#endif /* _JTUX_UTIL_H_ */
