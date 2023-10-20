package filippos.bagordakis.agora.agora;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Agora {
	
	@Value("Athens")
	private String id;

	
	
}
