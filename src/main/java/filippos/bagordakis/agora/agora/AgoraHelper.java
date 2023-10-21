package filippos.bagordakis.agora.agora;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import filippos.bagordakis.agora.agora.data.dto.AckoledgmentDTO;
import filippos.bagordakis.agora.agora.data.dto.GreetingDTO;
import filippos.bagordakis.agora.agora.data.dto.HeartbeatDTO;

public class AgoraHelper {

	public static ObjectMapper getObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();

		objectMapper.registerSubtypes(new NamedType(GreetingDTO.class, "greeting"),
				new NamedType(HeartbeatDTO.class, "heartbeat"), new NamedType(AckoledgmentDTO.class, "acknoledgment"));

		return objectMapper;
	}

}
