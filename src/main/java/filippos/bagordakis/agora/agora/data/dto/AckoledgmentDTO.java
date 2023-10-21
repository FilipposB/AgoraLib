package filippos.bagordakis.agora.agora.data.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("acknoledgment")
public class AckoledgmentDTO  extends BaseDTO  {

	public AckoledgmentDTO(String id) {
		super(id);
	}
	
	
	public AckoledgmentDTO() {
		
	}

}
