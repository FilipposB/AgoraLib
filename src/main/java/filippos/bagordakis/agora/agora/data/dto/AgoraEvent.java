package filippos.bagordakis.agora.agora.data.dto;

import org.springframework.context.ApplicationEvent;

public class AgoraEvent extends ApplicationEvent {
	private static final long serialVersionUID = -9018409911630405844L;

	private final Object data;

	public AgoraEvent(Object source, Object data) {
		super(source);
		this.data = data;
	}

	public Object getData() {
		return data;
	}

}
