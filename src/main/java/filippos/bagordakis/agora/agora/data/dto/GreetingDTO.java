package filippos.bagordakis.agora.agora.data.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("greeting")
public class GreetingDTO extends BaseDTO  {
	
	private String name;

	public GreetingDTO() {
		
	}
	
	public GreetingDTO(String id, String name) {
		super(id);
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
