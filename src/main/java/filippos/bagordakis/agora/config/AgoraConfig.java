package filippos.bagordakis.agora.config;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.core.JsonProcessingException;

import filippos.bagordakis.agora.agora.Agora;
import filippos.bagordakis.agora.agora.data.event.AgoraEvent;
import filippos.bagordakis.agora.stoa.annotation.Dose;
import filippos.bagordakis.agora.stoa.annotation.Pare;
import filippos.bagordakis.agora.stoa.annotation.Stoa;
import filippos.bagordakis.agora.stoa.enums.ResponseTypesEnum;
import filippos.bagordakis.agora.stoa.settings.StoaMethodSettings;
import filippos.bagordakis.agora.stoa.settings.StoaMethodSettings.Builder;
import filippos.bagordakis.agora.stoa.settings.StoaSettings;
import jakarta.annotation.PostConstruct;

@Import({ Agora.class})
public class AgoraConfig implements BeanFactoryPostProcessor, BeanPostProcessor, ApplicationEventPublisherAware  {

	private static Logger log = LoggerFactory.getLogger(AgoraConfig.class);
	
	@Autowired
	private  ApplicationEventPublisher applicationEventPublisher;

	@PostConstruct
	public void init() {
		log.info("Agora is now open");
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		Reflections reflections = new Reflections("");
		Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(Stoa.class);
		for (Class<?> proxyInterface : annotated) {
			if (!proxyInterface.isInterface()) {
				continue;
			}
			Class<?> iface = proxyInterface;
			Stoa stoaAnnotation = iface.getAnnotation(Stoa.class);
			String beanName = stoaAnnotation.value().equals("") ? proxyInterface.getSimpleName()
					: stoaAnnotation.value();
			beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);
			Object proxy = createProxyObject(iface, stoaAnnotation);
			beanFactory.registerSingleton(beanName, proxy);
		}
	}

	private StoaSettings extractSettings(Class<?> iface, Stoa stoaAnnotation) {
		StoaSettings requestClientSettings = new StoaSettings();
		for (Method method : iface.getMethods()) {
			requestClientSettings = extractDataFromMethod(requestClientSettings, method);
		}
		return requestClientSettings;
	}

	private StoaSettings extractDataFromMethod(StoaSettings stoaSettings, Method method) {
//		List<Annotation> stoaAnnotations = Arrays.stream(method.getAnnotations())
//				.filter(x -> x.annotationType().isAnnotationPresent(StoaAnnotation.class)).toList();
//
//		if (stoaAnnotations.isEmpty()) {
//			throw new RuntimeException(
//					"No annotated methods found on interface " + method.getDeclaringClass().getName());
//		} else if (stoaAnnotations.size() > 1) {
//			throw new RuntimeException("Multiple StoaAnnotation annotations found on method " + method.getName());
//		}
//
//		Annotation stoaAnnotation = stoaAnnotations.get(0);
//
//		String value;
//		try {
//			Method valueMethod = stoaAnnotation.getClass().getMethod("value");
//			value = (String) valueMethod.invoke(stoaAnnotation);
//		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
//				| SecurityException e) {
//			throw new RuntimeException("value method wasn't found for annotation " + stoaAnnotation.getClass());
//		}

		ResponseTypesEnum type = ResponseTypesEnum.BODY;
		Class<?> returnType = method.getReturnType();
		Type responseType = method.getGenericReturnType();
		if (responseType instanceof ParameterizedType) {
			if (returnType == Optional.class) {
				type = ResponseTypesEnum.OPTIONAL;
			} else {
				throw new RuntimeException(returnType + " is not supported");
			}
			responseType = ((ParameterizedType) responseType).getActualTypeArguments()[0];
		}
		Class<?> responseClass = (Class<?>) responseType;
		Builder builder = new StoaMethodSettings.Builder(responseClass, type);

		stoaSettings.addMethodSettings(method, builder.build());
		return stoaSettings;
	}

	private Object createProxyObject(Class<?> iface, Stoa stoaAnnotation) {
		StoaSettings stoaSettings = extractSettings(iface, stoaAnnotation);
		log.info("Proxing {}", iface);
		return Proxy.newProxyInstance(iface.getClassLoader(), new Class[] { iface }, (proxyObj, method, args) -> {
			return proxyCode(proxyObj, method, args, stoaSettings.getMethodSettings(method));
		});
	}

	private Object proxyCode(Object proxyObj, Method method, Object[] args, StoaMethodSettings settings) throws JsonProcessingException {
		if (method.isAnnotationPresent(Pare.class)) {
			log.info("Pare got executed");
			applicationEventPublisher.publishEvent(new AgoraEvent(this, "Pare"));
		} else if (method.isAnnotationPresent(Dose.class)) {
			log.info("Dose got executed");
			applicationEventPublisher.publishEvent(new AgoraEvent(this, "Dose"));
		}
		return null;
	}

	@Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

}
