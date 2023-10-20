package filippos.bagordakis.agora.stoa.settings;

import filippos.bagordakis.agora.stoa.enums.ResponseTypesEnum;

public class StoaMethodSettings {

	private final Class<?> returnType;
	private final ResponseTypesEnum responseTypesEnum;

	private StoaMethodSettings(Builder builder) {
		this.returnType = builder.returnType;
		this.responseTypesEnum = builder.type;
	}

	public Class<?> getReturnType() {
		return returnType;
	}
	
	public ResponseTypesEnum getResponseTypesEnum() {
		return responseTypesEnum;
	}

	public static class Builder {
		private final Class<?> returnType;
		private final ResponseTypesEnum type;
		
		public Builder(Class<?> returnType, ResponseTypesEnum type) {
			this.returnType = returnType;
			this.type = type;
		}

		public StoaMethodSettings build() {
			return new StoaMethodSettings(this);
		}

	}
}
