package filippos.bagordakis.agora.agora.data.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class BaseDTO {

}
