package filippos.bagordakis.agora.kripteia;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import filippos.bagordakis.agora.common.helper.AgoraHelper;

public record KrypteiaInfo(Object bean, Method method, Class<?> firstParamType) {

	public void execute(String json) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		method.invoke(bean, AgoraHelper.convert(json, firstParamType));
	}
	
}
