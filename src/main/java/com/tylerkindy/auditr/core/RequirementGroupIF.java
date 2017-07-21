package com.tylerkindy.auditr.core;

import java.util.Collection;
import org.immutables.value.Value.Immutable;

@Immutable
@MyStyle
public interface RequirementGroupIF {
  String getName();
  RequirementStatus getStatus();
  Collection<Requirement> getRequirements();
}