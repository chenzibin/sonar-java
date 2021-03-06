/*
 * SonarQube Java
 * Copyright (C) 2012-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.cfg;

import org.junit.jupiter.api.Test;
import org.sonar.java.model.JParserTestUtils;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.StatementTree;

import static org.assertj.core.api.Assertions.assertThat;

class VariableReadExtractorTest {

  private static MethodTree buildMethodTree(String methodCode) {
    CompilationUnitTree cut = JParserTestUtils.parse("class A { int field1; int field2; " + methodCode + " }");
    return (MethodTree) ((ClassTree) cut.types().get(0)).members().get(2);
  }

  @Test
  void should_extract_local_vars_read() {
    MethodTree methodTree = buildMethodTree("void foo(boolean a) { new Object() { void foo() { System.out.println(a);} };  }");
    StatementTree statementTree = methodTree.block().body().get(0);
    VariableReadExtractor extractor = new VariableReadExtractor(methodTree.symbol(), false);
    statementTree.accept(extractor);
    assertThat(extractor.usedVariables()).hasSize(1);
  }

  @Test
  void should_not_extract_local_vars_written() throws Exception {
    MethodTree methodTree = buildMethodTree("void foo(boolean a) { new Object() { void foo() { new A().field1 = 0; a = false;} };  }");
    StatementTree statementTree = methodTree.block().body().get(0);
    VariableReadExtractor extractor = new VariableReadExtractor(methodTree.symbol(), false);
    statementTree.accept(extractor);
    assertThat(extractor.usedVariables()).isEmpty();
    methodTree = buildMethodTree("void foo(boolean a) { new Object() { void foo() { a = !a;} };  }");
    statementTree = methodTree.block().body().get(0);
    extractor = new VariableReadExtractor(methodTree.symbol(), false);
    statementTree.accept(extractor);
    assertThat(extractor.usedVariables()).hasSize(1);
  }

  @Test
  void should_extract_fields_read() {
    MethodTree methodTree = buildMethodTree(
      "void foo(int a) { bar(p -> { System.out.println(a + field1); foo(this.field2); }); } " +
      "void bar(java.util.function.Consumer<Object> consumer) {}"
    );
    StatementTree statementTree = methodTree.block().body().get(0);
    VariableReadExtractor extractor = new VariableReadExtractor(methodTree.symbol(), true);
    statementTree.accept(extractor);
    // local variable "a" and fields "field1" and "field2"
    assertThat(extractor.usedVariables()).hasSize(3);
  }

  @Test
  void should_not_extract_fields_written() throws Exception {
    MethodTree methodTree = buildMethodTree("void foo(boolean a) { new Object() { void foo() { new A().field1 = 0; a = false;} };  }");
    StatementTree statementTree = methodTree.block().body().get(0);
    VariableReadExtractor extractor = new VariableReadExtractor(methodTree.symbol(), true);
    statementTree.accept(extractor);
    // local variable "a" and field "field"
    assertThat(extractor.usedVariables()).isEmpty();
  }

  @Test
  void should_not_extract_variable_declared() throws Exception {
    MethodTree methodTree = buildMethodTree("void foo(boolean a) { boolean b = a; }");
    StatementTree statementTree = methodTree.block().body().get(0);
    VariableReadExtractor extractor = new VariableReadExtractor(methodTree.symbol(), true);
    statementTree.accept(extractor);
    // only "a" should be detected
    assertThat(extractor.usedVariables()).hasSize(1);
  }

  @Test
  void should_return_symbol_once() {
    MethodTree methodTree = buildMethodTree("void foo(boolean a) { new Object() { void foo() { foo(a); bar(a); } }; }");
    StatementTree statementTree = methodTree.block().body().get(0);
    VariableReadExtractor extractor = new VariableReadExtractor(methodTree.symbol(), false);
    statementTree.accept(extractor);
    assertThat(extractor.usedVariables()).hasSize(1);
  }
}
