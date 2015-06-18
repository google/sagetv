#define JSTR_GET_DECL(s_c, s)\
	const char *s_c = (*env)->GetStringUTFChars(env, s, NULL);

#define JTHROW_neg1(e)\
	if ((intptr_t)(e) == (intptr_t)-1)\
		(void)setup_throw_errno(env, errno);

#define JTHROW_null(e)\
	if ((e) == NULL)\
		(void)setup_throw_errno(env, errno);

#define JTHROW_nzero(e)\
	if ((e) != 0)\
		(void)setup_throw_errno(env, errno);

#define JTHROW_rv(e)\
	{\
		int JTHROW_rv_r;\
		if ((JTHROW_rv_r = (e)) > 0)\
			(void)setup_throw_errno(env, JTHROW_rv_r);\
	}

#define JSTR_REL(s_c, s)\
	(*env)->ReleaseStringUTFChars(env, s, s_c);

#define JSTR_NULLTEST(s_c)\
	if (s_c == NULL)\
		return;

#define JSTR_NULLTEST_V(s_c, v)\
	if (s_c == NULL)\
		return v;

#define JSTR_RETURN(r)\
	return (*env)->NewStringUTF(env, r);
