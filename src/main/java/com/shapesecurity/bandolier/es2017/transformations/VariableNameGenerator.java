package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.ImmutableSet;

import javax.annotation.Nonnull;
import java.util.Iterator;

public class VariableNameGenerator implements Iterator<String> {

	@Nonnull
	private ImmutableSet<String> declaredVariables;

	private int ordinal = 0;
	private int currentLength = 1;
	private static final int lengthPower = 52;
	private int currentLengthPower = lengthPower;

	@Nonnull
	private static final char[] alphabet = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM".toCharArray();
	@Nonnull
	private static final ImmutableSet<String> keywords = ImmutableList.of(
			"instanceof",
			"in",
			"async",
			"await",
			"enum",
			"delete",
			"typeof",
			"void",
			"break",
			"case",
			"catch",
			"class",
			"continue",
			"debugger",
			"default",
			"do",
			"else",
			"export",
			"extends",
			"finally",
			"for",
			"function",
			"if",
			"import",
			"let",
			"new",
			"return",
			"super",
			"switch",
			"this",
			"throw",
			"try",
			"var",
			"while",
			"with",
			"null",
			"true",
			"false",
			"yield",
			"arguments",
			"eval"
	).uniqByEquality();

	public VariableNameGenerator(@Nonnull ImmutableSet<String> declaredVariables) {
		this.declaredVariables = declaredVariables;
	}

	@Override
	public boolean hasNext() {
		return true;
	}

	// this idea here is to generate all possible names, starting with the smallest, that do not conflict with our existing script, nor previously generated names
	@Override
	public String next() {
		String identifier;
		do {
			StringBuilder identifierBuilder = new StringBuilder();
			int currentOrdinal = ordinal;
			for (int i = 0; i < currentLength; i++) {
				identifierBuilder.append(alphabet[currentOrdinal % lengthPower]);
				currentOrdinal /= lengthPower;
			}
			identifier = identifierBuilder.toString();
			ordinal++;
			if (ordinal >= currentLengthPower) {
				ordinal = 0;
				currentLengthPower *= lengthPower;
				currentLength++;
			}
		} while (identifier.length() < 1 || declaredVariables.contains(identifier) || keywords.contains(identifier));
		return identifier;
	}

	public void blacklist(@Nonnull String name) {
		declaredVariables = declaredVariables.put(name);
	}

}
