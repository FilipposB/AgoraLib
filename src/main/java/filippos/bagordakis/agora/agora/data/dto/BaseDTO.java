package filippos.bagordakis.agora.agora.data.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class BaseDTO {

	private String id;

	public BaseDTO(String id) {
		this.id = id;
	}
	
	public BaseDTO() {
		
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
