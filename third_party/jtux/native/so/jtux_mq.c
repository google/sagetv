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

#include "defs.h"
#include <mqueue.h>

JNIEXPORT void JNICALL Java_jtux_UPosixIPC_mq_1close(JNIEnv *env, jclass obj,
  jlong mqd)
{
	JTHROW_neg1(mq_close((mqd_t)(intptr_t)mqd))
}

JNIEXPORT void JNICALL Java_jtux_UPosixIPC_mq_1getattr(JNIEnv *env, jclass obj,
  jlong mqd, jobject attr)
{
	jclass cls = (*env)->FindClass(env, "jtux/UPosixIPC$s_mq_attr");
	struct mq_attr attr_c;
	int r;

	JTHROW_neg1(r = mq_getattr((mqd_t)(intptr_t)mqd, &attr_c))
	if (r != -1) {
		if (!field_ctoj_long(env, cls, "mq_flags", attr, attr_c.mq_flags))
			return;
		if (!field_ctoj_long(env, cls, "mq_maxmsg", attr, attr_c.mq_maxmsg))
			return;
		if (!field_ctoj_long(env, cls, "mq_msgsize", attr, attr_c.mq_msgsize))
			return;
		if (!field_ctoj_long(env, cls, "mq_curmsgs", attr, attr_c.mq_curmsgs))
			return;
	}
}

JNIEXPORT void JNICALL Java_jtux_UPosixIPC_mq_1notify(JNIEnv *env, jclass obj,
  jlong mqd, jobject ep)
{
	jclass cls = (*env)->FindClass(env, "jtux/UProcess$s_sigevent");
	jclass cls_u_sigval_int = (*env)->FindClass(env, "jtux/UProcess$u_sigval_int");
	jclass cls_u_sigval_ptr = (*env)->FindClass(env, "jtux/UProcess$u_sigval_ptr");
	jfieldID fid;
	jobject sigev_value;
	struct sigevent sigevent_buf, *ep_c = &sigevent_buf;
	long sival_ptr;

	if (ep == NULL)
		ep_c = NULL;
	else {
		if (!field_jtoc_int(env, cls, "sigev_notify", ep, &ep_c->sigev_notify))
			return;
		if (!field_jtoc_int(env, cls, "sigev_signo", ep, &ep_c->sigev_signo))
			return;
		if ((fid = (*env)->GetFieldID(env, cls, "sigev_value", "Ljtux/UProcess$u_sigval;")) == NULL)
			return;
		if ((sigev_value = (*env)->GetObjectField(env, ep, fid)) == NULL) {
			JNU_ThrowByName(env, "NullPointerException", "sigev_value field not initialized");
			return;
		}
		if ((*env)->IsInstanceOf(env, sigev_value, cls_u_sigval_int)) {
			if (!field_jtoc_int(env, cls_u_sigval_int, "sival_int", sigev_value,
			  &ep_c->sigev_value.sival_int))
				return;
		}
		else if ((*env)->IsInstanceOf(env, sigev_value, cls_u_sigval_ptr)) {
			if (!field_jtoc_long(env, cls_u_sigval_ptr, "sival_ptr", sigev_value,
			  &sival_ptr))
				return;
			ep_c->sigev_value.sival_ptr = (void *)(intptr_t)sival_ptr;
		}
		else {
			(void)setup_throw_errno(env, EINVAL);
			return;
		}
	}
	JTHROW_neg1(mq_notify((mqd_t)(intptr_t)mqd, ep_c))
}

JNIEXPORT jlong JNICALL Java_jtux_UPosixIPC_mq_1open__Ljava_lang_String_2I(JNIEnv *env, jclass obj,
  jstring name, jint flags)
{
	JSTR_GET_DECL(name_c, name)
	mqd_t mqd;

	JSTR_NULLTEST_V(name_c, -1)
	JTHROW_neg1(mqd = mq_open(name_c, flags))
	JSTR_REL(name_c, name)
	return (intptr_t)mqd;
}

JNIEXPORT jlong JNICALL Java_jtux_UPosixIPC_mq_1open__Ljava_lang_String_2IILjtux_UPosixIPC_00024s_1mq_1attr_2(JNIEnv *env, jclass obj,
  jstring name, jint flags, jint perms, jobject attr)
{
	JSTR_GET_DECL(name_c, name)
	mqd_t mqd;
	struct mq_attr attr_c_buf, *attr_c = &attr_c_buf;
	jclass cls = (*env)->FindClass(env, "jtux/UPosixIPC$s_mq_attr");

	JSTR_NULLTEST_V(name_c, -1)
	if (attr == NULL)
		attr_c = NULL;
	else {
		if (!field_jtoc_long(env, cls, "mq_flags", attr, &attr_c->mq_flags))
			return -1;
		if (!field_jtoc_long(env, cls, "mq_maxmsg", attr, &attr_c->mq_maxmsg))
			return -1;
		if (!field_jtoc_long(env, cls, "mq_msgsize", attr, &attr_c->mq_msgsize))
			return -1;
		if (!field_jtoc_long(env, cls, "mq_curmsgs", attr, &attr_c->mq_curmsgs))
			return -1;
	}
	JTHROW_neg1(mqd = mq_open(name_c, flags, perms, attr_c))
	JSTR_REL(name_c, name)
	return (intptr_t)mqd;
}

JNIEXPORT jint JNICALL Java_jtux_UPosixIPC_mq_1receive(JNIEnv *env, jclass obj,
  jlong mqd, jbyteArray msg, jint msgsize, jobject priority)
{
	ssize_t n;
	void *msgp_c;
	int priority_c;
	jclass cls = (*env)->FindClass(env, "jtux/UUtil$IntHolder");

	if ((msgp_c = (*env)->GetByteArrayElements(env, msg, NULL)) == NULL)
		return -1;
	JTHROW_neg1(n = mq_receive((mqd_t)(intptr_t)mqd, msgp_c, msgsize, &priority_c))
	(*env)->ReleaseByteArrayElements(env, msg, msgp_c, 0);
	if (priority != NULL && n != -1) {
		if (!field_ctoj_int(env, cls, "value", priority, priority_c))
			return -1;
	}
	return n;
}

JNIEXPORT void JNICALL Java_jtux_UPosixIPC_mq_1send(JNIEnv *env, jclass obj,
  jlong mqd, jbyteArray msg, jint msgsize, jint priority)
{
	void *msgp_c;

	if ((msgp_c = (*env)->GetByteArrayElements(env, msg, NULL)) == NULL)
		return;
	JTHROW_neg1(mq_send((mqd_t)(intptr_t)mqd, msgp_c, msgsize, priority))
	(*env)->ReleaseByteArrayElements(env, msg, msgp_c, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_jtux_UPosixIPC_mq_1setattr(JNIEnv *env, jclass obj,
  jlong mqd, jobject attr, jobject oattr)
{
	jclass cls = (*env)->FindClass(env, "jtux/UPosixIPC$s_mq_attr");
	struct mq_attr attr_c, oattr_buf, *oattr_c = &oattr_buf;
	int r;

	if (oattr == NULL)
		oattr_c = NULL;
	if (!field_jtoc_long(env, cls, "mq_flags", attr, &attr_c.mq_flags))
		return;
	if (!field_jtoc_long(env, cls, "mq_maxmsg", attr, &attr_c.mq_maxmsg))
		return;
	if (!field_jtoc_long(env, cls, "mq_msgsize", attr, &attr_c.mq_msgsize))
		return;
	if (!field_jtoc_long(env, cls, "mq_curmsgs", attr, &attr_c.mq_curmsgs))
		return;
	JTHROW_neg1(r = mq_setattr((mqd_t)(intptr_t)mqd, &attr_c, oattr_c))
	if (oattr != NULL && r != -1) {
		if (!field_ctoj_long(env, cls, "mq_flags", oattr, oattr_c->mq_flags))
			return;
		if (!field_ctoj_long(env, cls, "mq_maxmsg", oattr, oattr_c->mq_maxmsg))
			return;
		if (!field_ctoj_long(env, cls, "mq_msgsize", oattr, oattr_c->mq_msgsize))
			return;
		if (!field_ctoj_long(env, cls, "mq_curmsgs", oattr, oattr_c->mq_curmsgs))
			return;
	}
}

JNIEXPORT jint JNICALL Java_jtux_UPosixIPC_mq_1timedreceive(JNIEnv *env, jclass obj,
  jlong mqd, jbyteArray msg, jint msgsize, jobject priority, jobject tmout)
{
#if _XOPEN_VERSION >= 600
// Following compiled but not tested
	ssize_t n;
	void *msgp_c;
	int priority_c;
	jclass cls_IntHolder = (*env)->FindClass(env, "jtux/UUtil$IntHolder");
	jclass cls_timespec = (*env)->FindClass(env, "jtux/UProcess$s_timespec");
	struct timespec tmout_c;
	long sec;

	if ((msgp_c = (*env)->GetByteArrayElements(env, msg, NULL)) == NULL)
		return -1;
	if (!field_jtoc_long(env, cls_timespec, "tv_sec", tmout, &sec))
		return -1;
	tmout_c.tv_sec = (time_t)sec;
	if (!field_jtoc_long(env, cls_timespec, "tv_nsec", tmout, &tmout_c.tv_nsec))
		return -1;
	JTHROW_neg1(n = mq_timedreceive((mqd_t)(intptr_t)mqd, msgp_c, msgsize, &priority_c,
	  &tmout_c))
	(*env)->ReleaseByteArrayElements(env, msg, msgp_c, 0);
	if (priority != NULL && n != -1) {
		if (!field_ctoj_int(env, cls_IntHolder, "value", priority, priority_c))
			return -1;
	}
	return n;
#else
	(void)setup_throw_errno(env, ENOSYS);
	return -1;
#endif
}

JNIEXPORT void JNICALL Java_jtux_UPosixIPC_mq_1timedsend(JNIEnv *env, jclass obj,
  jlong mqd, jbyteArray msg, jint msgsize, jint priority, jobject tmout)
{
#if _XOPEN_VERSION >= 600
// Following compiled but not tested
	void *msgp_c;
	jclass cls_timespec = (*env)->FindClass(env, "jtux/UProcess$s_timespec");
	struct timespec tmout_c;
	long sec;

	if ((msgp_c = (*env)->GetByteArrayElements(env, msg, NULL)) == NULL)
		return;
	if (!field_jtoc_long(env, cls_timespec, "tv_sec", tmout, &sec))
		return;
	tmout_c.tv_sec = (time_t)sec;
	if (!field_jtoc_long(env, cls_timespec, "tv_nsec", tmout, &tmout_c.tv_nsec))
		return;
	JTHROW_neg1(mq_timedsend((mqd_t)(intptr_t)mqd, msgp_c, msgsize, priority,
	  &tmout_c))
	(*env)->ReleaseByteArrayElements(env, msg, msgp_c, JNI_ABORT);
#else
	(void)setup_throw_errno(env, ENOSYS);
#endif
}

JNIEXPORT void JNICALL Java_jtux_UPosixIPC_mq_1unlink(JNIEnv *env, jclass obj,
  jstring name)
{
	JSTR_GET_DECL(name_c, name)

	JSTR_NULLTEST(name_c)
	JTHROW_neg1(mq_unlink(name_c))
	JSTR_REL(name_c, name)
}
