package org.thebubbleindex.model;

public class China extends BubbleIndexTimeseries {
	
	protected China() {}
	
	public China(final String name, final String symbol, final String dtype, final String keywords) {
		this.symbol = symbol;
		this.dtype = dtype;		
		this.name = name;
		this.keywords = keywords;
	}
}
