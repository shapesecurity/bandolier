package com.shapesecurity.bandolier;

public class RenameState {
	private int state;

	public RenameState() {
		this.state = 0;
	}

	public int next() {
		return this.state++;
	}
}
