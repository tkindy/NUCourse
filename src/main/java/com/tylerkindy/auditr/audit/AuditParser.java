package com.tylerkindy.auditr.audit;

import com.google.inject.Inject;
import com.tylerkindy.auditr.core.Audit;
import com.tylerkindy.auditr.core.CatalogCourse;
import com.tylerkindy.auditr.core.RemainingCoursesOperator;
import com.tylerkindy.auditr.core.Requirement;
import com.tylerkindy.auditr.core.RequirementGroup;
import com.tylerkindy.auditr.core.RequirementStatus;
import com.tylerkindy.auditr.db.repos.CatalogCourseRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditParser {

  private static final Logger LOG = LoggerFactory.getLogger(AuditParser.class);
  private static final Pattern REQUIREMENT_GROUP_TITLE_PATTERN = Pattern.compile("^(NO|IP|OK)\\s+(.+)$");
  private static final Pattern REQUIREMENT_TITLE_PATTERN = Pattern.compile("^(\\+|-|IP\\+|IP-)\\s+(.+)$");
  private static final Pattern COURSE_CONJUNCTION_PATTERN = Pattern.compile("^Complete the following");
  private static final Pattern STARTED_COURSE_LINE_PATTERN = Pattern.compile("^((?:FL|SP|S1|S2)\\d{2}) (\\w+?)\\s*(\\d+).*$");

  @Inject
  public AuditParser() {
  }

  public Audit parse(String html) {
    return parse(Jsoup.parse(html));
  }

  private Audit parse(Document document) {
    Collection<RequirementGroup> requirementGroups =
        document.select("a[href=\"#linkback\"]").stream()
            .map(this::parseRequirementGroup)
            .collect(Collectors.toSet());

    return Audit.builder()
        .setRequirementGroups(requirementGroups)
        .build();
  }

  private RequirementGroup parseRequirementGroup(Element backLink) {
    Element requirementGroupTitle = backLink.nextElementSibling();
    String groupTitleText = requirementGroupTitle.text().trim();

    Matcher titleMatcher = REQUIREMENT_GROUP_TITLE_PATTERN.matcher(groupTitleText);

    if (!titleMatcher.matches()) {
      throw new AuditParsingException("Requirement group title", groupTitleText);
    }

    RequirementStatus status = parseRequirementGroupStatus(titleMatcher.group(1));
    String name = titleMatcher.group(2);

    Collection<Requirement> requirements = new HashSet<>();
    Element curSection = requirementGroupTitle;

    while ((curSection = curSection.nextElementSibling()).is("p")) {
      if (curSection.children().isEmpty()) {
        continue;
      }

      requirements.add(parseRequirement(curSection));
    }

    return RequirementGroup.builder()
        .setName(name)
        .setStatus(status)
        .setRequirements(requirements)
        .build();
  }

  private RequirementStatus parseRequirementGroupStatus(String status) {
    switch (status) {
      case "NO":
        return RequirementStatus.NOT_STARTED;
      case "IP":
        return RequirementStatus.IN_PROGRESS;
      case "OK":
        return RequirementStatus.COMPLETED;
    }

    throw new AuditParsingException("Requirement group status", status);
  }

  private Requirement parseRequirement(Element section) {
    String titleText = section.child(0).text().trim();
    Matcher titleMatcher = REQUIREMENT_TITLE_PATTERN.matcher(titleText);

    if (!titleMatcher.matches()) {
      throw new AuditParsingException("Requirement title", titleText);
    }

    String name = titleMatcher.group(2);
    RequirementStatus status = parseRequirementStatus(titleMatcher.group(1));
    RemainingCoursesOperator operator = parseRemainingCoursesOperator(section);

    return Requirement.builder()
        .setName(name)
        .setStatus(status)
        .setRemainingCoursesOperator(operator)
        .build();
  }

  private RequirementStatus parseRequirementStatus(String status) {
    switch (status) {
      case "+":
        return RequirementStatus.COMPLETED;

      case "IP+":
      case "IP-":
        return RequirementStatus.IN_PROGRESS;

      case "-":
        return RequirementStatus.NOT_STARTED;
    }

    throw new AuditParsingException("Requirement status", status);
  }

  private RemainingCoursesOperator parseRemainingCoursesOperator(Element section) {
    Elements headerElements = section.select("i");

    if (headerElements.size() < 2) {
      return RemainingCoursesOperator.NONE;
    }

    String operatorText = headerElements.get(1).text().trim();

    if (COURSE_CONJUNCTION_PATTERN.matcher(operatorText).find()) {
      return RemainingCoursesOperator.AND;
    }

    return RemainingCoursesOperator.OR;
  }
}