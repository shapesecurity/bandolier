package com.shapesecurity.bandolier.es2017.bundlers;

import javax.annotation.Nonnull;

public final class BundlerOptions {

	public enum ImportUnresolvedResolutionStrategy {
		COMPILE_ERROR,
		DEFAULT_TO_UNDEFINED,
		THROW_ON_REFERENCE,
		NOTHING
	}

	public enum ExportStrategy {
		EXPLICIT,
		ALL_GLOBALS
	}

	public enum DangerLevel {
		SAFE, // to spec, should be used unless one wants compile errors when optimizations are impossible (or depends on Module[Symbol.toStringTag] behavior)
		BALANCED, // removes TDZ handling, Symbol.toStringTag, throwOnCircularDependency provides greedy safety for TDZ removal, however, Symbol.toStringTag remains unsafe.
		DANGEROUS // removes read-only protection for imports, does nothing over BALANCED with throwOnImportAssignment enabled.
	}

	@Nonnull
	public final ImportUnresolvedResolutionStrategy importUnresolvedResolutionStrategy;
	@Nonnull
	public final ExportStrategy exportStrategy;
	@Nonnull
	public final DangerLevel dangerLevel;
	public final boolean throwOnCircularDependency;
	public final boolean throwOnImportAssignment;


	public BundlerOptions(@Nonnull ImportUnresolvedResolutionStrategy importUnresolvedResolutionStrategy, @Nonnull ExportStrategy exportStrategy, @Nonnull DangerLevel dangerLevel, boolean throwOnCircularDependency, boolean throwOnImportAssignment) {
		this.importUnresolvedResolutionStrategy = importUnresolvedResolutionStrategy;
		this.exportStrategy = exportStrategy;
		this.dangerLevel = dangerLevel;
		this.throwOnCircularDependency = throwOnCircularDependency;
		this.throwOnImportAssignment = throwOnImportAssignment;
	}

	public static final BundlerOptions NODE_OPTIONS = new BundlerOptions(ImportUnresolvedResolutionStrategy.DEFAULT_TO_UNDEFINED, ExportStrategy.ALL_GLOBALS, DangerLevel.SAFE, false, false);

	public static final BundlerOptions SPEC_OPTIONS = new BundlerOptions(ImportUnresolvedResolutionStrategy.COMPILE_ERROR, ExportStrategy.EXPLICIT, DangerLevel.SAFE, false, false);

	public static final BundlerOptions DEFAULT_OPTIONS = new BundlerOptions(ImportUnresolvedResolutionStrategy.COMPILE_ERROR, ExportStrategy.EXPLICIT, DangerLevel.SAFE, true, true);

	public BundlerOptions withDangerLevel(@Nonnull DangerLevel dangerLevel) {
		return new BundlerOptions(importUnresolvedResolutionStrategy, exportStrategy, dangerLevel, throwOnCircularDependency, throwOnImportAssignment);
	}

	public BundlerOptions withThrowOnCircularDependency(boolean throwOnCircularDependency) {
		return new BundlerOptions(importUnresolvedResolutionStrategy, exportStrategy, dangerLevel, throwOnCircularDependency, throwOnImportAssignment);
	}

	public BundlerOptions withThrowOnImportAssignment(boolean throwOnImportAssignment) {
		return new BundlerOptions(importUnresolvedResolutionStrategy, exportStrategy, dangerLevel, throwOnCircularDependency, throwOnImportAssignment);
	}

}
