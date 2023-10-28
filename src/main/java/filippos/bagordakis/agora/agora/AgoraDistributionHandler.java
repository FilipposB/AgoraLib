package filippos.bagordakis.agora.agora;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import filippos.bagordakis.agora.common.dto.RequestDTO;
import filippos.bagordakis.agora.kripteia.Kripteia;
import filippos.bagordakis.agora.kripteia.KrypteiaInfo;
import filippos.bagordakis.agora.kripteia.Krypteias;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class AgoraDistributionHandler {

	private static final Logger log = LoggerFactory.getLogger(AgoraDistributionHandler.class);

	private final ApplicationContext applicationContext;
	private final ExecutorService executor;
	private final Map<String, KrypteiaInfo> krypteia;
	private boolean started = false;

	public AgoraDistributionHandler(ApplicationContext applicationContext) {
		this.executor = Executors.newWorkStealingPool();
		this.applicationContext = applicationContext;
		krypteia = wireUpKrypteia();
	}

	@PostConstruct
	public void start() {
		started = true;
	}

	private Map<String, KrypteiaInfo> wireUpKrypteia() {
		Map<String, KrypteiaInfo> valuesToMethods = new ConcurrentHashMap<>();

		String[] beanNames = applicationContext.getBeanNamesForAnnotation(Krypteias.class);
		for (String beanName : beanNames) {

			Object bean = applicationContext.getBean(beanName);
			Class<?> beanClass = bean.getClass();

			for (Method method : beanClass.getDeclaredMethods()) {
				if (method.isAnnotationPresent(Kripteia.class)) {
					Kripteia kripteia = method.getAnnotation(Kripteia.class);
					String value = kripteia.value();

					if (valuesToMethods.containsKey(value)) {
						throw new RuntimeException("Duplicate value for @Kripteia annotation found: " + value);
					}

					Class<?> firstParamType = null;
					if (method.getParameterCount() > 0) {
						firstParamType = method.getParameterTypes()[0];
					}

					KrypteiaInfo info = new KrypteiaInfo(bean, method, firstParamType);

					valuesToMethods.put(value, info);
				}
			}
			log.info(valuesToMethods.toString());
		}

		return valuesToMethods;

	}

	@PreDestroy
	public void stop() {
		if (started) {
			executor.shutdown();
		}
	}

	public void feedQue(RequestDTO requestDTO) {
		this.executor.submit(new Task(requestDTO));
	}

	private class Task implements Runnable {

		private final RequestDTO task;

		public Task(RequestDTO task) {
			this.task = task;
		}

		@Override
		public void run() {
			try {
				krypteia.get(task.getKeyword()).execute(task.getJsonData());
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}

	}

}
