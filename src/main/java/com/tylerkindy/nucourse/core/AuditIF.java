package com.tylerkindy.nucourse.core;

import java.util.Collection;
import org.immutables.value.Value.Immutable;

@Immutable
@MyStyle
public interface AuditIF {

  Collection<RequirementGroup> getRequirementGroups();
}
