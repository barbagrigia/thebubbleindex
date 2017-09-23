package org.thebubbleindex.model;

import javax.persistence.Entity;

@Entity
public class Austria extends BubbleIndexTimeseries {
    
	protected Austria() {}
	
	public Austria(final String name, final String symbol, final String type, final String keywords) {
		this.symbol = symbol;
		this.type = type;		
		this.name = name;
		this.keywords = keywords;
	}
}