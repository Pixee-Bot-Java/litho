/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho;

import static android.content.Context.ACCESSIBILITY_SERVICE;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.RED;
import static androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
import static androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO;
import static androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
import static androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.facebook.litho.Column.create;
import static com.facebook.litho.LayoutOutput.getLayoutOutput;
import static com.facebook.litho.NodeInfo.ENABLED_SET_FALSE;
import static com.facebook.litho.NodeInfo.FOCUS_SET_TRUE;
import static com.facebook.litho.NodeInfo.SELECTED_SET_TRUE;
import static com.facebook.litho.SizeSpec.AT_MOST;
import static com.facebook.litho.SizeSpec.EXACTLY;
import static com.facebook.litho.SizeSpec.UNSPECIFIED;
import static com.facebook.litho.SizeSpec.makeSizeSpec;
import static com.facebook.yoga.YogaAlign.CENTER;
import static com.facebook.yoga.YogaAlign.FLEX_START;
import static com.facebook.yoga.YogaEdge.ALL;
import static com.facebook.yoga.YogaEdge.BOTTOM;
import static com.facebook.yoga.YogaEdge.HORIZONTAL;
import static com.facebook.yoga.YogaEdge.LEFT;
import static com.facebook.yoga.YogaEdge.RIGHT;
import static com.facebook.yoga.YogaEdge.TOP;
import static com.facebook.yoga.YogaJustify.SPACE_AROUND;
import static com.facebook.yoga.YogaPositionType.ABSOLUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

import android.graphics.Color;
import android.graphics.Rect;
import android.util.SparseArray;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;
import com.facebook.litho.config.TempComponentsConfigurations;
import com.facebook.litho.testing.LegacyLithoViewRule;
import com.facebook.litho.testing.TestComponent;
import com.facebook.litho.testing.TestDrawableComponent;
import com.facebook.litho.testing.TestLayoutComponent;
import com.facebook.litho.testing.TestSizeDependentComponent;
import com.facebook.litho.testing.TestViewComponent;
import com.facebook.litho.testing.Whitebox;
import com.facebook.litho.testing.inlinelayoutspec.InlineLayoutSpec;
import com.facebook.litho.testing.logging.TestComponentsLogger;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import com.facebook.litho.widget.ComponentCaching;
import com.facebook.litho.widget.ItemCardComponent;
import com.facebook.litho.widget.ItemCardComponentSpec;
import com.facebook.litho.widget.LayoutSpecLifecycleTester;
import com.facebook.litho.widget.SolidColor;
import com.facebook.litho.widget.TestNullLayoutComponent;
import com.facebook.litho.widget.Text;
import com.facebook.rendercore.Function;
import com.facebook.rendercore.RenderTreeNode;
import com.facebook.rendercore.utils.MeasureSpecUtils;
import com.facebook.yoga.YogaAlign;
import com.facebook.yoga.YogaEdge;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAccessibilityManager;

@RunWith(LithoTestRunner.class)
public class LayoutStateCalculateTest {

  public final @Rule LegacyLithoViewRule mLegacyLithoViewRule = new LegacyLithoViewRule();

  private ComponentContext mContext;

  @Before
  public void setup() throws Exception {
    TempComponentsConfigurations.setShouldAddHostViewForRootComponent(true);
    // invdalidate the cached accessibility value before each test runs so that we don't
    // have a value already cached.  If we don't do this, accessibility tests will fail when run
    // after non-accessibility tests, and vice-versa.
    AccessibilityUtils.invalidateCachedIsAccessibilityEnabled();
    mContext = mLegacyLithoViewRule.getContext();
  }

  @After
  public void validate() {
    validateMockitoUsage();
  }

  @Test
  public void testNoUnnecessaryLayoutOutputsForLayoutSpecs() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return Column.create(c)
                .child(Column.create(c).child(TestDrawableComponent.create(c)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(2);
  }

  @Test
  public void testLayoutOutputsForRootInteractiveLayoutSpecs() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).child(TestDrawableComponent.create(c)).wrapInView().build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
  }

  @Test
  public void testLayoutOutputsForSpecsWithTouchExpansion() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c).widthPx(100).heightPx(10))
                .child(
                    Row.create(c)
                        .viewTag(new Object())
                        .child(TestDrawableComponent.create(c).widthPx(20).heightPx(90))
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .clickHandler(c.newEventHandler(1))
                                .widthPx(50)
                                .heightPx(50)
                                .touchExpansionPx(YogaEdge.ALL, 5)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(6);

    final ViewNodeInfo viewNodeInfo =
        getLayoutOutput(layoutState.getMountableOutputAt(4)).getViewNodeInfo();
    assertThat(viewNodeInfo.getTouchBoundsExpansion()).isEqualTo(new Rect(5, 5, 5, 5));

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(4)).getNodeInfo();
    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getClickHandler()).isNotNull();
    assertThat(nodeInfo.getLongClickHandler()).isNull();
    assertThat(nodeInfo.getFocusChangeHandler()).isNull();
    assertThat(nodeInfo.getTouchHandler()).isNull();
  }

  @Test
  public void testLayoutOutputsForSpecsWithClickHandling() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .clickHandler(c.newEventHandler(1)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo();
    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getClickHandler()).isNotNull();
    assertThat(nodeInfo.getLongClickHandler()).isNull();
    assertThat(nodeInfo.getFocusChangeHandler()).isNull();
    assertThat(nodeInfo.getTouchHandler()).isNull();
  }

  @Test
  public void testLayoutOutputsForSpecsWithLongClickHandling() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .longClickHandler(c.newEventHandler(1)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo();
    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getClickHandler()).isNull();
    assertThat(nodeInfo.getLongClickHandler()).isNotNull();
    assertThat(nodeInfo.getFocusChangeHandler()).isNull();
    assertThat(nodeInfo.getTouchHandler()).isNull();
  }

  @Test
  public void testLayoutOutputsForSpecsWithFocusChangeHandling() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .focusChangeHandler(c.newEventHandler(1)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo();
    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getClickHandler()).isNull();
    assertThat(nodeInfo.getLongClickHandler()).isNull();
    assertThat(nodeInfo.getFocusChangeHandler()).isNotNull();
    assertThat(nodeInfo.getTouchHandler()).isNull();
  }

  @Test
  public void testLayoutOutputsForSpecsWithInterceptTouchHandling() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .interceptTouchHandler(c.newEventHandler(1)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo();
    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getClickHandler()).isNull();
    assertThat(nodeInfo.getLongClickHandler()).isNull();
    assertThat(nodeInfo.getInterceptTouchHandler()).isNotNull();
    assertThat(nodeInfo.getTouchHandler()).isNull();
  }

  @Test
  public void testLayoutOutputsForSpecsWithTouchHandling() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .touchHandler(c.newEventHandler(1)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo();
    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getTouchHandler()).isNotNull();
    assertThat(nodeInfo.getClickHandler()).isNull();
    assertThat(nodeInfo.getLongClickHandler()).isNull();
    assertThat(nodeInfo.getFocusChangeHandler()).isNull();
  }

  @Test
  public void testLayoutOutputsForDeepLayoutSpecs() {
    final int paddingSize = 5;
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .backgroundColor(0xFFFF0000)
                .child(
                    Row.create(c)
                        .justifyContent(SPACE_AROUND)
                        .alignItems(CENTER)
                        .positionType(ABSOLUTE)
                        .positionPx(LEFT, 50)
                        .positionPx(TOP, 50)
                        .positionPx(RIGHT, 200)
                        .positionPx(BOTTOM, 50)
                        .child(Text.create(c).text("textLeft1"))
                        .child(Text.create(c).text("textRight1"))
                        .paddingPx(ALL, paddingSize)
                        .wrapInView())
                .child(
                    Row.create(c)
                        .justifyContent(SPACE_AROUND)
                        .alignItems(CENTER)
                        .positionType(ABSOLUTE)
                        .positionPx(LEFT, 200)
                        .positionPx(TOP, 50)
                        .positionPx(RIGHT, 50)
                        .positionPx(BOTTOM, 50)
                        .child(
                            Text.create(c)
                                .text("textLeft2")
                                .wrapInView()
                                .paddingPx(ALL, paddingSize))
                        .child(TestViewComponent.create(c).wrapInView()))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Mountable Output Breakdown:
    // 0) Root
    // 1) Host with bg
    // 2) Row 1
    // 3) Text drawable 1
    // 4) Text drawable 2
    // 5) Row 2
    // 6) Text drawable
    // 7) Text view component

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(8);

    // Check quantity of HostComponents.
    int totalHosts = 0;
    for (int i = 0; i < layoutState.getMountableOutputCount(); i++) {
      final Component mountedComponent = getComponentAt(layoutState, i);
      if (isHostComponent(mountedComponent)) {
        totalHosts++;
      }
    }
    assertThat(totalHosts).isEqualTo(4);

    // Check all the Layouts are in the correct position.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 2))).isTrue();
    assertThat(getComponentAt(layoutState, 3)).isInstanceOf(Text.class);
    assertThat(getComponentAt(layoutState, 4)).isInstanceOf(Text.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 5))).isTrue();
    assertThat(getComponentAt(layoutState, 6)).isInstanceOf(Text.class);
    assertThat(getComponentAt(layoutState, 7)).isInstanceOf(TestViewComponent.class);

    // Check the text within the TextComponents.
    assertThat(getTextFromTextComponent(layoutState, 3)).isEqualTo("textLeft1");
    assertThat(getTextFromTextComponent(layoutState, 4)).isEqualTo("textRight1");
    assertThat(getTextFromTextComponent(layoutState, 6)).isEqualTo("textLeft2");

    final Rect textLayoutBounds = layoutState.getMountableOutputAt(6).getAbsoluteBounds(new Rect());
    final Rect textBackgroundBounds =
        layoutState.getMountableOutputAt(5).getAbsoluteBounds(new Rect());

    assertThat(textLayoutBounds.left - paddingSize).isEqualTo(textBackgroundBounds.left);
    assertThat(textLayoutBounds.top - paddingSize).isEqualTo(textBackgroundBounds.top);
    assertThat(textLayoutBounds.right + paddingSize).isEqualTo(textBackgroundBounds.right);
    assertThat(textLayoutBounds.bottom + paddingSize).isEqualTo(textBackgroundBounds.bottom);
  }

  @Test
  public void testLayoutOutputMountBounds() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .widthPx(30)
                .heightPx(30)
                .wrapInView()
                .child(
                    create(c)
                        .widthPx(10)
                        .heightPx(10)
                        .marginPx(ALL, 10)
                        .wrapInView()
                        .child(TestDrawableComponent.create(c).widthPx(10).heightPx(10)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    Rect mountBounds = new Rect();

    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 30, 30));

    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 30, 30));

    mountBounds = layoutState.getMountableOutputAt(2).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(10, 10, 20, 20));

    mountBounds = layoutState.getMountableOutputAt(3).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 10, 10));
  }

  @Test
  public void testLayoutOutputsForDeepLayoutSpecsWithBackground() {
    final int paddingSize = 5;
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .backgroundColor(0xFFFF0000)
                .child(
                    Row.create(c)
                        .justifyContent(SPACE_AROUND)
                        .alignItems(CENTER)
                        .positionType(ABSOLUTE)
                        .positionPx(LEFT, 50)
                        .positionPx(TOP, 50)
                        .positionPx(RIGHT, 200)
                        .positionPx(BOTTOM, 50)
                        .child(Text.create(c).text("textLeft1"))
                        .child(Text.create(c).text("textRight1"))
                        .backgroundColor(0xFFFF0000)
                        .foregroundColor(0xFFFF0000)
                        .paddingPx(ALL, paddingSize)
                        .wrapInView())
                .child(
                    Row.create(c)
                        .justifyContent(SPACE_AROUND)
                        .alignItems(CENTER)
                        .positionType(ABSOLUTE)
                        .positionPx(LEFT, 200)
                        .positionPx(TOP, 50)
                        .positionPx(RIGHT, 50)
                        .positionPx(BOTTOM, 50)
                        .child(
                            Text.create(c)
                                .text("textLeft2")
                                .wrapInView()
                                .backgroundColor(0xFFFF0000)
                                .paddingPx(ALL, paddingSize))
                        .child(
                            TestViewComponent.create(c)
                                .backgroundColor(0xFFFF0000)
                                .foregroundColor(0x0000FFFF)
                                .paddingPx(ALL, paddingSize)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(8);

    // Check quantity of HostComponents.
    int totalHosts = 0;
    for (int i = 0; i < layoutState.getMountableOutputCount(); i++) {
      final Component mountedComponent = getComponentAt(layoutState, i);
      if (isHostComponent(mountedComponent)) {
        totalHosts++;
      }
    }
    assertThat(totalHosts).isEqualTo(4);

    // Check all the Layouts are in the correct position.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 2))).isTrue();
    assertThat(getComponentAt(layoutState, 3)).isInstanceOf(Text.class);
    assertThat(getComponentAt(layoutState, 4)).isInstanceOf(Text.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 5))).isTrue();
    assertThat(getComponentAt(layoutState, 6)).isInstanceOf(Text.class);
    assertThat(getComponentAt(layoutState, 7)).isInstanceOf(TestViewComponent.class);

    // Check the text within the TextComponents.
    assertThat(getTextFromTextComponent(layoutState, 3)).isEqualTo("textLeft1");
    assertThat(getTextFromTextComponent(layoutState, 4)).isEqualTo("textRight1");
    assertThat(getTextFromTextComponent(layoutState, 6)).isEqualTo("textLeft2");

    // Check background and foreground applied
    final ViewNodeInfo viewNodeInfo =
        getLayoutOutput(layoutState.getMountableOutputAt(7)).getViewNodeInfo();
    assertThat(viewNodeInfo).isNotNull();
    assertThat(viewNodeInfo.getBackground()).isNotNull();

    // Check padding all applied correctly
    assertThat(viewNodeInfo.getPaddingLeft() == paddingSize).isTrue();
    assertThat(viewNodeInfo.getPaddingTop() == paddingSize).isTrue();
    assertThat(viewNodeInfo.getPaddingRight() == paddingSize).isTrue();
    assertThat(viewNodeInfo.getPaddingBottom() == paddingSize).isTrue();
  }

  @Test
  public void testLayoutOutputsForMegaDeepLayoutSpecs() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c).wrapInView())
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestDrawableComponent.create(c))
                                .wrapInView())
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestDrawableComponent.create(c)))
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestViewComponent.create(c)))
                        .wrapInView())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(18);

    // Check quantity of HostComponents.
    int totalHosts = 0;
    for (int i = 0; i < layoutState.getMountableOutputCount(); i++) {
      final Component mountedComponent = getComponentAt(layoutState, i);
      if (isHostComponent(mountedComponent)) {
        totalHosts++;
      }
    }
    assertThat(totalHosts).isEqualTo(7);

    // Check all the Components match the right LayoutOutput positions.
    // Tree One.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 3)).isInstanceOf(TestDrawableComponent.class);

    // Tree Two.
    assertThat(isHostComponent(getComponentAt(layoutState, 4))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 5))).isTrue();
    assertThat(getComponentAt(layoutState, 6)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 7))).isTrue();
    assertThat(getComponentAt(layoutState, 8)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 9)).isInstanceOf(TestDrawableComponent.class);

    // Tree Three.
    assertThat(isHostComponent(getComponentAt(layoutState, 10))).isTrue();
    assertThat(getComponentAt(layoutState, 11)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 12)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 13)).isInstanceOf(TestDrawableComponent.class);

    // Tree Four.
    assertThat(isHostComponent(getComponentAt(layoutState, 14))).isTrue();
    assertThat(getComponentAt(layoutState, 15)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 16)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 17)).isInstanceOf(TestViewComponent.class);
  }

  @Test
  public void testLayoutOutputStableIds() {
    // Needed because of (legacy) usage two different InlineLayoutSpec that the test wants to treat
    // as the same component type.
    final int COMPONENT_IDENTITY = 12345;
    final Component component1 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(create(c).child(TestDrawableComponent.create(c)).contentDescription("cd0"))
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .contentDescription("cd1"))
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestViewComponent.create(c))
                        .contentDescription("cd2"))
                .build();
          }
        };
    final Component component2 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(create(c).child(TestDrawableComponent.create(c)).contentDescription("cd0"))
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .contentDescription("cd1"))
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestViewComponent.create(c))
                        .contentDescription("cd2"))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component1,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));

    final LayoutState sameComponentLayoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component2,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));

    assertThat(sameComponentLayoutState.getMountableOutputCount())
        .isEqualTo(layoutState.getMountableOutputCount());

    for (int i = 0; i < layoutState.getMountableOutputCount(); i++) {
      assertThat(sameComponentLayoutState.getMountableOutputAt(i).getRenderUnit().getId())
          .isEqualTo(layoutState.getMountableOutputAt(i).getRenderUnit().getId());
    }
  }

  @Test
  public void testLayoutOutputStableIdsForMegaDeepComponent() {
    // Needed because of (legacy) usage two different InlineLayoutSpec that the test wants to treat
    // as the same component type.
    final int COMPONENT_IDENTITY = 12345;
    final Component component1 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c).wrapInView())
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestDrawableComponent.create(c))
                                .wrapInView())
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestDrawableComponent.create(c)))
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestViewComponent.create(c)))
                        .wrapInView())
                .build();
          }
        };

    final Component component2 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c).wrapInView())
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestDrawableComponent.create(c))
                                .wrapInView())
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestDrawableComponent.create(c)))
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(
                            create(c)
                                .child(TestDrawableComponent.create(c))
                                .child(TestViewComponent.create(c)))
                        .wrapInView())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component1,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));

    final LayoutState sameComponentLayoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component2,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));
    assertThat(sameComponentLayoutState.getMountableOutputCount())
        .isEqualTo(layoutState.getMountableOutputCount());

    for (int i = 0; i < layoutState.getMountableOutputCount(); i++) {
      assertThat(sameComponentLayoutState.getMountableOutputAt(i).getRenderUnit().getId())
          .isEqualTo(layoutState.getMountableOutputAt(i).getRenderUnit().getId());
    }
  }

  @Test
  public void testPartiallyStableIds() {
    // Needed because of (legacy) usage two different InlineLayoutSpec that the test wants to treat
    // as the same component type.
    final int COMPONENT_IDENTITY = 12345;
    final Component component1 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .child(TestDrawableComponent.create(c))
                .build();
          }
        };
    final Component component2 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c)))
                .build();
          }
        };

    final LayoutState layoutState1 =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component1,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));

    final LayoutState layoutState2 =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component2,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));

    assertThat(layoutState2.getMountableOutputAt(0).getRenderUnit().getId())
        .isEqualTo(layoutState1.getMountableOutputAt(0).getRenderUnit().getId());
    assertThat(layoutState2.getMountableOutputAt(1).getRenderUnit().getId())
        .isEqualTo(layoutState1.getMountableOutputAt(1).getRenderUnit().getId());
    assertThat(layoutState1.getMountableOutputCount()).isEqualTo(3);
    assertThat(layoutState2.getMountableOutputCount()).isEqualTo(4);
  }

  @Test
  public void testDifferentIds() {
    // Needed because of (legacy) usage two different InlineLayoutSpec that the test wants to treat
    // as the same component type.
    final int COMPONENT_IDENTITY = 12345;
    final Component component1 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return Column.create(c)
                .child(TestDrawableComponent.create(c))
                .child(TestDrawableComponent.create(c))
                .build();
          }
        };
    final Component component2 =
        new InlineLayoutSpec(COMPONENT_IDENTITY) {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return Column.create(c)
                .child(TestDrawableComponent.create(c).wrapInView())
                .child(
                    Column.create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .wrapInView())
                .build();
          }
        };

    final LayoutState layoutState1 =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component1,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));

    final LayoutState layoutState2 =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component2,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(20, EXACTLY));

    assertThat(layoutState1.getMountableOutputAt(1).getRenderUnit().getId())
        .isNotEqualTo(layoutState2.getMountableOutputAt(1).getRenderUnit().getId());
  }

  @Test
  public void testLayoutOutputsWithInteractiveLayoutSpecAsLeafs() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .child(create(c).child(TestLayoutComponent.create(c)).wrapInView())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(getComponentAt(layoutState, 1)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 2))).isTrue();
  }

  private static Component getComponentAt(final LayoutState layoutState, final int index) {
    return getLayoutOutput(layoutState.getMountableOutputAt(index)).getComponent();
  }

  private static CharSequence getTextFromTextComponent(
      final LayoutState layoutState, final int index) {
    return Whitebox.getInternalState(
        getLayoutOutput(layoutState.getMountableOutputAt(index)).getComponent(), "text");
  }

  private static boolean isHostComponent(final Component component) {
    return component instanceof HostComponent;
  }

  @Test
  public void testNoMeasureOnNestedComponentWithSameSpecs() {
    final ComponentContext baseContext = new ComponentContext(getApplicationContext());
    final ComponentContext c =
        ComponentContext.withComponentTree(baseContext, ComponentTree.create(baseContext).build());
    final LayoutState layoutState = new LayoutState(c);
    final LayoutStateContext layoutStateContext =
        new LayoutStateContext(layoutState, c.getComponentTree());
    final RenderStateContext renderStateContext = layoutStateContext.getRenderStateContext();
    c.setLayoutStateContext(layoutStateContext);
    Whitebox.setInternalState(layoutState, "mLayoutStateContext", layoutStateContext);

    final Size size = new Size();
    final TestComponent innerComponent =
        TestDrawableComponent.create(c, 0, 0, true, true, false, false).build();
    final int widthSpec = makeSizeSpec(100, EXACTLY);
    final int heightSpec = makeSizeSpec(100, EXACTLY);
    innerComponent.measure(c, widthSpec, heightSpec, size);

    final LithoLayoutResult internalNode =
        renderStateContext.getCache().getCachedResult(innerComponent);
    internalNode.setLastWidthSpec(widthSpec);
    internalNode.setLastHeightSpec(heightSpec);
    internalNode.setLastMeasuredWidth(internalNode.getWidth());
    internalNode.setLastMeasuredHeight(internalNode.getHeight());

    innerComponent.resetInteractions();

    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(Row.create(c).child(innerComponent).widthPx(100).heightPx(100))
                .build();
          }
        };

    calculateLayoutState(
        mLegacyLithoViewRule.getComponentTree().getContext(),
        component,
        -1,
        makeSizeSpec(100, EXACTLY),
        makeSizeSpec(100, EXACTLY));

    // Currently, we create a clone of Component object and measure gets called on that cloned
    // Component.
    // Here we are checking if measure was called on Component object which was created
    // in test (actually it is getting called on cloned object but in useStatelessComponent we don't
    // clone the Component object)
    // Therefore different behaviour in useStatelessComponent
    assertThat(innerComponent.wasMeasureCalled()).isTrue();
  }

  @Test
  public void testNoMeasureOnNestedComponentWithNewMeasureSpecExact() {
    final ComponentContext baseContext = new ComponentContext(getApplicationContext());
    final ComponentContext c =
        ComponentContext.withComponentTree(baseContext, ComponentTree.create(baseContext).build());
    final LayoutState layoutState = new LayoutState(c);
    final LayoutStateContext layoutStateContext =
        new LayoutStateContext(layoutState, c.getComponentTree());
    final RenderStateContext renderStateContext = layoutStateContext.getRenderStateContext();
    c.setLayoutStateContext(layoutStateContext);
    Whitebox.setInternalState(layoutState, "mLayoutStateContext", layoutStateContext);

    final Size size = new Size();
    final TestComponent innerComponent =
        TestDrawableComponent.create(c, 0, 0, true, true, false, false).build();
    final int widthSpec = makeSizeSpec(100, AT_MOST);
    final int heightSpec = makeSizeSpec(100, AT_MOST);
    innerComponent.measure(c, widthSpec, heightSpec, size);

    final LithoLayoutResult internalNode =
        renderStateContext.getCache().getCachedResult(innerComponent);
    internalNode.setLastWidthSpec(widthSpec);
    internalNode.setLastHeightSpec(heightSpec);
    internalNode.setLastMeasuredWidth(100);
    internalNode.setLastMeasuredHeight(100);

    innerComponent.resetInteractions();

    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(Row.create(c).child(innerComponent).widthPx(100).heightPx(100))
                .build();
          }
        };

    calculateLayoutState(
        mLegacyLithoViewRule.getComponentTree().getContext(),
        component,
        -1,
        makeSizeSpec(100, EXACTLY),
        makeSizeSpec(100, EXACTLY));

    // Currently, we create a clone of Component object and measure gets called on that cloned
    // Component.
    // Here we are checking if measure was called on Component object which was created
    // in test (actually it is getting called on cloned object but in useStatelessComponent we don't
    // clone the Component object)
    // Therefore different behaviour in useStatelessComponent
    assertThat(innerComponent.wasMeasureCalled()).isTrue();
  }

  @Test
  public void testNoMeasureOnNestedComponentWithNewMeasureSpecOldUnspecified() {
    final ComponentContext baseContext = new ComponentContext(getApplicationContext());
    final ComponentContext c =
        ComponentContext.withComponentTree(baseContext, ComponentTree.create(baseContext).build());
    final LayoutState layoutState = new LayoutState(c);
    final LayoutStateContext layoutStateContext =
        new LayoutStateContext(layoutState, c.getComponentTree());
    final RenderStateContext renderStateContext = layoutStateContext.getRenderStateContext();
    c.setLayoutStateContext(layoutStateContext);
    Whitebox.setInternalState(layoutState, "mLayoutStateContext", layoutStateContext);

    final Size size = new Size();
    final TestComponent innerComponent =
        TestDrawableComponent.create(c, 0, 0, true, true, false, false).build();
    final int widthSpec = makeSizeSpec(0, UNSPECIFIED);
    final int heightSpec = makeSizeSpec(0, UNSPECIFIED);
    innerComponent.measure(c, widthSpec, heightSpec, size);

    final LithoLayoutResult internalNode =
        renderStateContext.getCache().getCachedResult(innerComponent);
    internalNode.setLastWidthSpec(widthSpec);
    internalNode.setLastHeightSpec(heightSpec);
    internalNode.setLastMeasuredWidth(99);
    internalNode.setLastMeasuredHeight(99);

    innerComponent.resetInteractions();

    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).child(innerComponent).build();
          }
        };

    calculateLayoutState(
        mLegacyLithoViewRule.getComponentTree().getContext(),
        component,
        -1,
        makeSizeSpec(100, AT_MOST),
        makeSizeSpec(100, AT_MOST));

    // Currently, we create a clone of Component object and measure gets called on that cloned
    // Component.
    // Here we are checking if measure was called on Component object which was created
    // in test (actually it is getting called on cloned object but in useStatelessComponent we don't
    // clone the Component object)
    // Therefore different behaviour in useStatelessComponent
    assertThat(innerComponent.wasMeasureCalled()).isTrue();
  }

  @Test
  public void testNoMeasureOnNestedComponentWithOldAndNewAtMost() {
    final ComponentContext baseContext = new ComponentContext(getApplicationContext());
    final ComponentContext c =
        ComponentContext.withComponentTree(baseContext, ComponentTree.create(baseContext).build());
    final LayoutState layoutState = new LayoutState(c);
    final LayoutStateContext layoutStateContext =
        new LayoutStateContext(layoutState, c.getComponentTree());
    final RenderStateContext renderStateContext = layoutStateContext.getRenderStateContext();
    c.setLayoutStateContext(layoutStateContext);
    Whitebox.setInternalState(layoutState, "mLayoutStateContext", layoutStateContext);

    final Size size = new Size();
    final TestComponent innerComponent =
        TestDrawableComponent.create(c, 0, 0, true, true, false, false).build();
    final int widthSpec = makeSizeSpec(100, AT_MOST);
    final int heightSpec = makeSizeSpec(100, AT_MOST);
    innerComponent.measure(c, widthSpec, heightSpec, size);

    final LithoLayoutResult internalNode =
        renderStateContext.getCache().getCachedResult(innerComponent);
    internalNode.setLastWidthSpec(widthSpec);
    internalNode.setLastHeightSpec(heightSpec);
    internalNode.setLastMeasuredWidth(50);
    internalNode.setLastMeasuredHeight(50);

    innerComponent.resetInteractions();

    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).child(Row.create(c).child(innerComponent).flexShrink(0)).build();
          }
        };

    calculateLayoutState(
        mLegacyLithoViewRule.getComponentTree().getContext(),
        component,
        -1,
        makeSizeSpec(50, AT_MOST),
        makeSizeSpec(50, AT_MOST));

    // Currently, we create a clone of Component object and measure gets called on that cloned
    // Component.
    // Here we are checking if measure was called on Component object which was created
    // in test (actually it is getting called on cloned object but in useStatelessComponent we don't
    // clone the Component object)
    // Therefore different behaviour in useStatelessComponent
    assertThat(innerComponent.wasMeasureCalled()).isTrue();
  }

  @Test
  public void testLayoutOutputsForTwiceNestedComponent() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c).child(create(c).child(TestDrawableComponent.create(c))).wrapInView())
                .child(
                    create(c)
                        .child(create(c).child(TestDrawableComponent.create(c)))
                        .child(create(c).child(TestDrawableComponent.create(c))))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(5);

    final long hostMarkerRoot = layoutState.getMountableOutputAt(0).getRenderUnit().getId();
    final long hostMarkerOne = layoutState.getMountableOutputAt(1).getRenderUnit().getId();

    // First output is the inner host for the click handler
    assertThat(getHostId(layoutState.getMountableOutputAt(1))).isEqualTo(hostMarkerRoot);

    // Second output is the child of the inner host
    assertThat(getHostId(layoutState.getMountableOutputAt(2))).isEqualTo(hostMarkerOne);

    // Third and fourth outputs are children of the root view.
    assertThat(getHostId(layoutState.getMountableOutputAt(3))).isEqualTo(hostMarkerRoot);
    assertThat(getHostId(layoutState.getMountableOutputAt(4))).isEqualTo(hostMarkerRoot);
  }

  @Test
  public void testLayoutOutputsForComponentWithBackgrounds() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .backgroundColor(0xFFFF0000)
                .foregroundColor(0xFFFF0000)
                .child(TestDrawableComponent.create(c))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    // Host generated with  background
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(
            getLayoutOutput(layoutState.getMountableOutputAt(1)).getViewNodeInfo().getBackground())
        .isNotNull();
  }

  @Test
  public void testLayoutOutputsForNonComponentClickableNode() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(create(c).child(TestDrawableComponent.create(c)).wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .wrapInView())
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestViewComponent.create(c))
                        .wrapInView())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(9);

    final long hostMarkerRoot = getHostId(layoutState.getMountableOutputAt(0));
    final long hostMarkerZero = getHostId(layoutState.getMountableOutputAt(1));
    final long hostMarkerTwo = getHostId(layoutState.getMountableOutputAt(4));
    final long hostMarkerThree = getHostId(layoutState.getMountableOutputAt(7));

    assertThat(getHostId(layoutState.getMountableOutputAt(1))).isEqualTo(hostMarkerRoot);
    assertThat(getHostId(layoutState.getMountableOutputAt(3))).isEqualTo(hostMarkerZero);
    assertThat(getHostId(layoutState.getMountableOutputAt(5))).isEqualTo(hostMarkerTwo);
    assertThat(getHostId(layoutState.getMountableOutputAt(6))).isEqualTo(hostMarkerZero);
    assertThat(getHostId(layoutState.getMountableOutputAt(8))).isEqualTo(hostMarkerThree);

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 3))).isTrue();
    assertThat(getComponentAt(layoutState, 4)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 5)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 6))).isTrue();
    assertThat(getComponentAt(layoutState, 7)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 8)).isInstanceOf(TestViewComponent.class);
  }

  @Test
  public void testLayoutOutputsForNonComponentContentDescriptionNode() {
    enableAccessibility();

    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(create(c).child(TestDrawableComponent.create(c)).contentDescription("cd0"))
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestDrawableComponent.create(c))
                        .contentDescription("cd1"))
                .child(
                    create(c)
                        .child(TestDrawableComponent.create(c))
                        .child(TestViewComponent.create(c))
                        .contentDescription("cd2"))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(9);

    final long hostMarkerRoot = getHostId(layoutState.getMountableOutputAt(0));
    final long hostMarkerZero = getHostId(layoutState.getMountableOutputAt(1));
    final long hostMarkerTwo = getHostId(layoutState.getMountableOutputAt(4));
    final long hostMarkerThree = getHostId(layoutState.getMountableOutputAt(7));

    assertThat(getHostId(layoutState.getMountableOutputAt(1))).isEqualTo(hostMarkerRoot);
    assertThat(getHostId(layoutState.getMountableOutputAt(3))).isEqualTo(hostMarkerZero);
    assertThat(getHostId(layoutState.getMountableOutputAt(5))).isEqualTo(hostMarkerTwo);
    assertThat(getHostId(layoutState.getMountableOutputAt(6))).isEqualTo(hostMarkerZero);
    assertThat(getHostId(layoutState.getMountableOutputAt(8))).isEqualTo(hostMarkerThree);

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 3))).isTrue();
    assertThat(getComponentAt(layoutState, 4)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 5)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 6))).isTrue();
    assertThat(getComponentAt(layoutState, 7)).isInstanceOf(TestDrawableComponent.class);
    assertThat(getComponentAt(layoutState, 8)).isInstanceOf(TestViewComponent.class);
  }

  @Test
  public void testLayoutOutputsForFocusableOnRoot() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).child(TestDrawableComponent.create(c)).focusable(true).build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    final RenderTreeNode rootOutput = layoutState.getMountableOutputAt(0);
    final RenderTreeNode hostOutput = layoutState.getMountableOutputAt(1);
    final RenderTreeNode drawableOutput = layoutState.getMountableOutputAt(2);

    assertThat(hostOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rootOutput.getRenderUnit().getId());
    assertThat(drawableOutput.getParent().getRenderUnit().getId())
        .isEqualTo(hostOutput.getRenderUnit().getId());

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo().getFocusState())
        .isEqualTo(FOCUS_SET_TRUE);
  }

  @Test
  public void testLayoutOutputsForFocusable() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(create(c).child(TestDrawableComponent.create(c)).focusable(true))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getNodeInfo()).isNull();
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo().getFocusState())
        .isEqualTo(FOCUS_SET_TRUE);
  }

  @Test
  public void testLayoutOutputsForSelectedOnRoot() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).child(TestDrawableComponent.create(c)).selected(true).build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
    final long hostMarkerZero = getHostId(layoutState.getMountableOutputAt(0));

    assertThat(getHostId(layoutState.getMountableOutputAt(1))).isEqualTo(hostMarkerZero);

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);

    assertThat(
            getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo().getSelectedState())
        .isEqualTo(SELECTED_SET_TRUE);
  }

  @Test
  public void testLayoutOutputsForSelected() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c).child(TestDrawableComponent.create(c)).focusable(true).selected(true))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getNodeInfo()).isNull();
    assertThat(
            getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo().getSelectedState())
        .isEqualTo(SELECTED_SET_TRUE);
  }

  @Test
  public void testLayoutOutputsForEnabledFalseDoesntWrap() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(create(c).child(TestDrawableComponent.create(c).enabled(false)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(2);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getNodeInfo()).isNull();
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getComponent().getSimpleName())
        .isEqualTo("HostComponent");

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getComponent().getSimpleName())
        .isEqualTo("TestDrawableComponent");
    assertThat(
            LayoutOutput.isTouchableDisabled(
                getLayoutOutput(layoutState.getMountableOutputAt(1)).getFlags()))
        .isTrue();
  }

  @Test
  public void testLayoutOutputsForEnabledFalseInInnerWrappedComponentDrawable() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .child(
                            TestDrawableComponent.create(c)
                                .clickHandler(c.newEventHandler(1))
                                .enabled(false)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    // Because the TestDrawableComponent is disabled, we don't wrap it in a host.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(2);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getNodeInfo()).isNull();
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getComponent())
        .isInstanceOf(HostComponent.class);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getComponent())
        .isInstanceOf(TestDrawableComponent.class);
    assertThat(
            LayoutOutput.isTouchableDisabled(
                getLayoutOutput(layoutState.getMountableOutputAt(1)).getFlags()))
        .isTrue();
  }

  @Test
  public void testLayoutOutputsForEnabledFalseInInnerComponentView() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(create(c).child(TestViewComponent.create(c).enabled(false)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(2);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getNodeInfo()).isNull();
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getComponent())
        .isInstanceOf(HostComponent.class);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getComponent())
        .isInstanceOf(TestViewComponent.class);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo().getEnabledState())
        .isEqualTo(ENABLED_SET_FALSE);
  }

  @Test
  public void testLayoutOutputsForEnabledFalseApplyToDescendent() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(
                    create(c)
                        .enabled(false)
                        .child(TestViewComponent.create(c).enabled(true))
                        .child(TestDrawableComponent.create(c).clickHandler(c.newEventHandler(1)))
                        .child(TestDrawableComponent.create(c).enabled(false)))
                .child(
                    create(c)
                        .child(TestViewComponent.create(c))
                        .child(TestDrawableComponent.create(c)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(6);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getNodeInfo()).isNull();
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(0)).getComponent())
        .isInstanceOf(HostComponent.class);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getComponent())
        .isInstanceOf(TestViewComponent.class);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo().getEnabledState())
        .isEqualTo(ENABLED_SET_FALSE);

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(2)).getComponent())
        .isInstanceOf(TestDrawableComponent.class);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(2)).getNodeInfo()).isNull();
    assertThat(
            LayoutOutput.isTouchableDisabled(
                getLayoutOutput(layoutState.getMountableOutputAt(2)).getFlags()))
        .isTrue();

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(3)).getComponent())
        .isInstanceOf(TestDrawableComponent.class);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(3)).getNodeInfo()).isNull();
    assertThat(
            LayoutOutput.isTouchableDisabled(
                getLayoutOutput(layoutState.getMountableOutputAt(3)).getFlags()))
        .isTrue();

    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(4)).getComponent())
        .isInstanceOf(TestViewComponent.class);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(4)).getNodeInfo()).isNull();
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(5)).getComponent())
        .isInstanceOf(TestDrawableComponent.class);
    assertThat(getLayoutOutput(layoutState.getMountableOutputAt(5)).getNodeInfo()).isNull();
    assertThat(
            LayoutOutput.isTouchableDisabled(
                getLayoutOutput(layoutState.getMountableOutputAt(5)).getFlags()))
        .isFalse();
  }

  @Test
  public void testLayoutOutputsForAccessibilityEnabled() {
    enableAccessibility();

    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return Row.create(c)
                .alignItems(CENTER)
                .paddingDip(ALL, 10)
                .contentDescription("This is root view")
                .child(TestDrawableComponent.create(c).widthDip(30).heightDip(30))
                .child(
                    TestDrawableComponent.create(c, true, true, true)
                        .flex(1)
                        .flexBasisDip(0)
                        .backgroundColor(RED)
                        .marginDip(HORIZONTAL, 10))
                .child(
                    Row.create(c)
                        .alignItems(CENTER)
                        .paddingDip(ALL, 10)
                        .contentDescription("This is a container")
                        .child(
                            TestDrawableComponent.create(c)
                                .widthDip(30)
                                .heightDip(30)
                                .contentDescription("This is an image"))
                        .child(
                            TestDrawableComponent.create(c, true, true, true)
                                .flex(1)
                                .flexBasisDip(0)
                                .marginDip(HORIZONTAL, 10)))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    // Mountable output breakdown:
    // 0) Root
    // 1) Row host 1
    // 2) Drawable 1
    // 3) Host for Drawable 2
    // 4) Drawable 2
    // 5) Row host 2
    // 6) Host for drawable 3
    // 7) Drawable 3
    // 8) Host for drawable 4
    // 9) Drawable 4

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(10);

    final RenderTreeNode rootOutput = layoutState.getMountableOutputAt(0);
    final RenderTreeNode rowOneOutput = layoutState.getMountableOutputAt(1);
    final RenderTreeNode drawableOnetOutput = layoutState.getMountableOutputAt(2);
    final RenderTreeNode drawableTwoHostOutput = layoutState.getMountableOutputAt(3);
    final RenderTreeNode drawableTwoOutput = layoutState.getMountableOutputAt(4);
    final RenderTreeNode rowTwoOutput = layoutState.getMountableOutputAt(5);
    final RenderTreeNode drawableThreeHostOutput = layoutState.getMountableOutputAt(6);
    final RenderTreeNode drawableThreeOutput = layoutState.getMountableOutputAt(7);
    final RenderTreeNode drawableFourHostOutput = layoutState.getMountableOutputAt(8);
    final RenderTreeNode drawableFourOutput = layoutState.getMountableOutputAt(9);

    assertThat(rowOneOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rootOutput.getRenderUnit().getId());
    assertThat(drawableOnetOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rowOneOutput.getRenderUnit().getId());
    assertThat(drawableTwoHostOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rowOneOutput.getRenderUnit().getId());
    assertThat(drawableTwoOutput.getParent().getRenderUnit().getId())
        .isEqualTo(drawableTwoHostOutput.getRenderUnit().getId());
    assertThat(rowTwoOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rowOneOutput.getRenderUnit().getId());
    assertThat(drawableThreeHostOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rowTwoOutput.getRenderUnit().getId());
    assertThat(drawableThreeOutput.getParent().getRenderUnit().getId())
        .isEqualTo(drawableThreeHostOutput.getRenderUnit().getId());
    assertThat(drawableFourHostOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rowTwoOutput.getRenderUnit().getId());
    assertThat(drawableFourOutput.getParent().getRenderUnit().getId())
        .isEqualTo(drawableFourHostOutput.getRenderUnit().getId());

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 3))).isTrue();
    assertThat(getComponentAt(layoutState, 4)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 5))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 6))).isTrue();
    assertThat(getComponentAt(layoutState, 7)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 8))).isTrue();
    assertThat(getComponentAt(layoutState, 9)).isInstanceOf(TestDrawableComponent.class);
  }

  @Test
  public void testLayoutOutputsWithImportantForAccessibility() {
    enableAccessibility();

    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .contentDescription("This is root view")
                .child(TestDrawableComponent.create(c).widthDip(30).heightDip(30))
                .child(
                    TestDrawableComponent.create(c, true, true, true)
                        .flex(1)
                        .flexBasisDip(0)
                        .backgroundColor(RED)
                        .marginDip(HORIZONTAL, 10)
                        .importantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO))
                .child(
                    Row.create(c)
                        .alignItems(CENTER)
                        .paddingDip(ALL, 10)
                        .importantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                        .child(
                            TestDrawableComponent.create(c)
                                .widthDip(30)
                                .heightDip(30)
                                .importantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES)
                                .contentDescription("This is an image")))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(8);

    // Breakdown of mountable output:
    // getMountableOutputAt(0) = inlineLayout
    // getMountableOutputAt(1) = root host
    // getMountableOutputAt(2) = drawable zero
    // getMountableOutputAt(3) = drawable one host
    // getMountableOutputAt(4) = drawable one
    // getMountableOutputAt(5) = row
    // getMountableOutputAt(6) = drawable two host
    // getMountableOutputAt(7) = drawable two

    final RenderTreeNode inlineLayoutOutput = layoutState.getMountableOutputAt(0);
    final RenderTreeNode rootHostOutput = layoutState.getMountableOutputAt(1);
    final RenderTreeNode drawableZeroOutput = layoutState.getMountableOutputAt(2);
    final RenderTreeNode drawableOneHostOutput = layoutState.getMountableOutputAt(3);
    final RenderTreeNode drawableOneOutput = layoutState.getMountableOutputAt(4);
    final RenderTreeNode rowOutput = layoutState.getMountableOutputAt(5);
    final RenderTreeNode drawableTwoHostOutput = layoutState.getMountableOutputAt(6);
    final RenderTreeNode drawableTwoOutput = layoutState.getMountableOutputAt(7);

    assertThat(drawableZeroOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rootHostOutput.getRenderUnit().getId());
    assertThat(drawableOneHostOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rootHostOutput.getRenderUnit().getId());
    assertThat(drawableOneOutput.getParent().getRenderUnit().getId())
        .isEqualTo(drawableOneHostOutput.getRenderUnit().getId());
    assertThat(rowOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rootHostOutput.getRenderUnit().getId());
    assertThat(drawableTwoHostOutput.getParent().getRenderUnit().getId())
        .isEqualTo(rowOutput.getRenderUnit().getId());
    assertThat(drawableTwoOutput.getParent().getRenderUnit().getId())
        .isEqualTo(drawableTwoHostOutput.getRenderUnit().getId());

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 3))).isTrue();
    assertThat(getComponentAt(layoutState, 4)).isInstanceOf(TestDrawableComponent.class);
    assertThat(isHostComponent(getComponentAt(layoutState, 5))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 6))).isTrue();
    assertThat(getComponentAt(layoutState, 7)).isInstanceOf(TestDrawableComponent.class);

    assertThat(IMPORTANT_FOR_ACCESSIBILITY_AUTO)
        .isEqualTo(getLayoutOutput(inlineLayoutOutput).getImportantForAccessibility());
    assertThat(IMPORTANT_FOR_ACCESSIBILITY_AUTO)
        .isEqualTo(getLayoutOutput(rootHostOutput).getImportantForAccessibility());
    assertThat(IMPORTANT_FOR_ACCESSIBILITY_AUTO)
        .isEqualTo(getLayoutOutput(drawableZeroOutput).getImportantForAccessibility());
    assertThat(IMPORTANT_FOR_ACCESSIBILITY_NO)
        .isEqualTo(getLayoutOutput(drawableOneHostOutput).getImportantForAccessibility());
    assertThat(IMPORTANT_FOR_ACCESSIBILITY_NO)
        .isEqualTo(getLayoutOutput(drawableOneOutput).getImportantForAccessibility());
    assertThat(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        .isEqualTo(getLayoutOutput(rowOutput).getImportantForAccessibility());
    assertThat(IMPORTANT_FOR_ACCESSIBILITY_YES)
        .isEqualTo(getLayoutOutput(drawableTwoHostOutput).getImportantForAccessibility());
    assertThat(IMPORTANT_FOR_ACCESSIBILITY_YES)
        .isEqualTo(getLayoutOutput(drawableTwoOutput).getImportantForAccessibility());
  }

  @Test
  public void testLayoutOutputsForClickHandlerAndViewTagsOnRoot() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .clickHandler(c.newEventHandler(1))
                .viewTags(new SparseArray<>())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
    final long hostMarkerZero = getHostId(layoutState.getMountableOutputAt(0));

    assertThat(getHostId(layoutState.getMountableOutputAt(1))).isEqualTo(hostMarkerZero);

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo();

    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getClickHandler()).isNotNull();
    assertThat(nodeInfo.getViewTags()).isNotNull();
  }

  @Test
  public void testLayoutOutputsForLongClickHandlerAndViewTagsOnRoot() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .longClickHandler(c.newEventHandler(1))
                .viewTags(new SparseArray<>())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    final long hostMarkerZero = getHostId(layoutState.getMountableOutputAt(0));

    assertThat(getHostId(layoutState.getMountableOutputAt(1))).isEqualTo(hostMarkerZero);

    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);

    final NodeInfo nodeInfo = getLayoutOutput(layoutState.getMountableOutputAt(1)).getNodeInfo();
    assertThat(nodeInfo).isNotNull();
    assertThat(nodeInfo.getLongClickHandler()).isNotNull();
    assertThat(nodeInfo.getViewTags()).isNotNull();
  }

  @Test
  public void testLayoutOutputsForForceWrappedComponent() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).child(TestDrawableComponent.create(c).wrapInView()).build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
    assertThat(getComponentAt(layoutState, 0)).isInstanceOf(HostComponent.class);
    assertThat(getComponentAt(layoutState, 1)).isInstanceOf(HostComponent.class);
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
  }

  @Test
  public void testLayoutOutputForRootNestedTreeComponent() {
    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            TestSizeDependentComponent.create(new ComponentContext(getApplicationContext()))
                .setFixSizes(true)
                .setDelegate(false)
                .build(),
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(4);
    Rect mountBounds = new Rect();
    // Check host.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 350, 200));
    assertThat(getHostId(layoutState.getMountableOutputAt(0))).isEqualTo(0);
    // Check NestedTree
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(5, 5, 55, 55));
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    mountBounds = layoutState.getMountableOutputAt(2).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 50, 50));
    assertThat(getComponentAt(layoutState, 3)).isInstanceOf(TestViewComponent.class);
    mountBounds = layoutState.getMountableOutputAt(3).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(8, 58, 342, 78));
  }

  @Test
  public void testLayoutOutputForDelegateNestedTreeComponentDelegate() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .paddingPx(ALL, 2)
                .child(
                    TestSizeDependentComponent.create(c)
                        .setFixSizes(true)
                        .setDelegate(true)
                        .marginPx(ALL, 11))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
    Rect mountBounds = new Rect();
    // Check host.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 350, 200));
    // Check NestedTree
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(13, 13, 63, 63));
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    mountBounds = layoutState.getMountableOutputAt(2).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 50, 50));
  }

  @Test
  public void testLayoutOutputForDelegateNestedTreeComponent() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .paddingPx(ALL, 2)
                .child(
                    TestSizeDependentComponent.create(c)
                        .setFixSizes(true)
                        .setDelegate(false)
                        .marginPx(ALL, 11))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(4);
    Rect mountBounds = new Rect();
    // Check host.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 350, 200));
    assertThat(getHostId(layoutState.getMountableOutputAt(0))).isEqualTo(0);
    // Check NestedTree
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(18, 18, 68, 68));
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    mountBounds = layoutState.getMountableOutputAt(2).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 50, 50));
    assertThat(getComponentAt(layoutState, 3)).isInstanceOf(TestViewComponent.class);
    mountBounds = layoutState.getMountableOutputAt(3).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(21, 71, 329, 91));
  }

  @Test
  public void testLayoutOutputForRootWithDelegateNestedTreeComponent() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return TestSizeDependentComponent.create(c)
                .setFixSizes(true)
                .setDelegate(false)
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(4);
    Rect mountBounds = new Rect();
    // Check host.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 350, 200));
    assertThat(getHostId(layoutState.getMountableOutputAt(0))).isEqualTo(0);
    // Check NestedTree
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(5, 5, 55, 55));
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    mountBounds = layoutState.getMountableOutputAt(2).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 50, 50));
    assertThat(getComponentAt(layoutState, 3)).isInstanceOf(TestViewComponent.class);
    mountBounds = layoutState.getMountableOutputAt(3).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(8, 58, 342, 78));
  }

  @Test
  public void testLayoutOutputRootWithPaddingOverridingDelegateNestedTreeComponent() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            final Component nestedTreeRootComponent =
                TestSizeDependentComponent.create(c).setFixSizes(true).setDelegate(false).build();

            return Wrapper.create(c).delegate(nestedTreeRootComponent).paddingPx(ALL, 10).build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    // Check total layout outputs.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(4);
    Rect mountBounds = new Rect();
    // Check host.
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 350, 200));
    assertThat(getHostId(layoutState.getMountableOutputAt(0))).isEqualTo(0);

    // Check NestedTree
    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(10, 10, 60, 60));
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(TestDrawableComponent.class);
    mountBounds = layoutState.getMountableOutputAt(2).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 50, 50));
    assertThat(getComponentAt(layoutState, 3)).isInstanceOf(TestViewComponent.class);
    mountBounds = layoutState.getMountableOutputAt(3).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(13, 63, 337, 83));
  }

  @Test
  public void testLayoutOutputForRootWithNullLayout() {
    final Component componentWithNullLayout =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return null;
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            componentWithNullLayout,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(0);
  }

  @Test
  public void testLayoutComponentForNestedTreeChildWithNullLayout() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).paddingPx(ALL, 2).child(TestNullLayoutComponent.create(c)).build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(350, EXACTLY),
            makeSizeSpec(200, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(1);
    Rect mountBounds = new Rect();
    assertThat(isHostComponent(getComponentAt(layoutState, 0))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 350, 200));
  }

  @Test
  public void testMeasure() {
    final int width = 50;
    final int height = 30;
    final ComponentContext c = new ComponentContext(getApplicationContext());
    final LayoutStateContext layoutStateContext = LayoutStateContext.getTestInstance(c);
    c.setLayoutStateContext(layoutStateContext); // TODO: To be deleted
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c).measuredWidth(width).measuredHeight(height))
                .build();
          }
        };

    int widthSpec = makeSizeSpec(width, AT_MOST);
    int heightSpec = makeSizeSpec(height, AT_MOST);

    final @Nullable ResolvedTree resolvedTree =
        Layout.createResolvedTree(
            Preconditions.checkNotNull(layoutStateContext), c, component, widthSpec, heightSpec);

    final LithoNode lithoNode = resolvedTree == null ? null : resolvedTree.getRoot();

    final LithoLayoutResult node =
        Layout.measureTree(
            Preconditions.checkNotNull(layoutStateContext),
            c.getAndroidContext(),
            lithoNode,
            widthSpec,
            heightSpec,
            null);

    assertThat(node.getWidth()).isEqualTo(width);
    assertThat(node.getHeight()).isEqualTo(height);
    assertThat(node.getChildCount()).isEqualTo(1);
    assertThat((node.getChildAt(0)).getWidth()).isEqualTo(width);
    assertThat((node.getChildAt(0)).getHeight()).isEqualTo(height);
  }

  @Test
  public void testNestedTreeComponentWithDoubleMeasurementsDoesntThrow() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return Row.create(c)
                .alignItems(YogaAlign.STRETCH)
                .paddingPx(YogaEdge.ALL, 2)
                .child(
                    TestSizeDependentComponent.create(c)
                        .setFixSizes(true)
                        .setDelegate(false)
                        .marginPx(YogaEdge.ALL, 11))
                .child(TestDrawableComponent.create(c).heightPx(200).widthPx(200))
                .build();
          }
        };

    calculateLayoutState(
        mLegacyLithoViewRule.getComponentTree().getContext(),
        component,
        -1,
        makeSizeSpec(350, EXACTLY),
        makeSizeSpec(0, UNSPECIFIED));

    // Testing that is not throwing an exception.
  }

  @Test
  public void testLayoutOutputForRootNestedTreeComponentWithAspectRatio() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestSizeDependentComponent.create(c).widthPx(100).aspectRatio(1))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(0, UNSPECIFIED),
            makeSizeSpec(0, UNSPECIFIED));

    Rect mountBounds = new Rect();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 100, 100));
  }

  @Test
  public void testLayoutOutputForRootNestedTreeComponentWithPercentParentSizeDefined() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .alignItems(FLEX_START)
                .widthPx(100)
                .heightPx(100)
                .child(
                    TestSizeDependentComponent.create(c)
                        .widthPercent(50)
                        .heightPercent(50)
                        .backgroundColor(0xFFFF0000))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(0, UNSPECIFIED),
            makeSizeSpec(0, UNSPECIFIED));

    Rect mountBounds = new Rect();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 100, 100));

    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 50, 50));
  }

  @Test
  public void testLayoutOutputForRootNestedTreeComponentWithPercent() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .alignItems(FLEX_START)
                .child(
                    TestSizeDependentComponent.create(c)
                        .setFixSizes(true)
                        .widthPercent(50)
                        .heightPercent(50)
                        .backgroundColor(0xFFFF0000))
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(0, UNSPECIFIED),
            makeSizeSpec(0, UNSPECIFIED));

    Rect mountBounds = new Rect();
    mountBounds = layoutState.getMountableOutputAt(0).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 60, 86));

    assertThat(isHostComponent(getComponentAt(layoutState, 1))).isTrue();
    mountBounds = layoutState.getMountableOutputAt(1).getBounds();
    assertThat(mountBounds).isEqualTo(new Rect(0, 0, 60, 86));
  }

  @Test
  public void testLayoutOutputsForComponentWithBorderColorNoBorderWidth() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .border(Border.create(c).color(ALL, GREEN).build())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    // No layout output generated related with borders
    // if borderColor is supplied but not borderWidth.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(2);
  }

  @Test
  public void testLayoutOutputsForComponentWithBorderWidthNoBorderColor() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .border(Border.create(c).widthPx(ALL, 10).build())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    // No layout output generated related with borders
    // if borderWidth supplied but not borderColor.
    assertThat(layoutState.getMountableOutputCount()).isEqualTo(2);
  }

  @Test
  public void testLayoutOutputsForComponentWithBorderWidthAllAndBorderColor() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .border(Border.create(c).widthPx(ALL, 10).color(ALL, GREEN).build())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    // Output at index 1 is BorderColorDrawable component.
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(DrawableComponent.class);
  }

  @Test
  public void testLayoutOutputsForComponentWithBorderWidthTopAndBorderColor() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c)
                .child(TestDrawableComponent.create(c))
                .border(Border.create(c).widthPx(TOP, 10).color(TOP, GREEN).build())
                .build();
          }
        };

    final LayoutState layoutState =
        calculateLayoutState(
            mLegacyLithoViewRule.getComponentTree().getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY));

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);

    // Output at index 1 is BorderColorDrawable component.
    assertThat(getComponentAt(layoutState, 2)).isInstanceOf(DrawableComponent.class);
  }

  @Test
  public void testWillRenderLayoutsOnce() {

    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final LayoutStateContext layoutStateContext = LayoutStateContext.getTestInstance(c);
    c.setLayoutStateContext(layoutStateContext); // TODO: To be deleted
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final Component component =
        Column.create(c)
            .child(
                LayoutSpecLifecycleTester.create(c).widthPx(100).heightPx(100).steps(steps).build())
            .build();

    Component.willRender(c, component);

    assertThat(LifecycleStep.getSteps(steps)).containsOnlyOnce(LifecycleStep.ON_CREATE_LAYOUT);

    steps.clear();

    final LithoNode cachedLayout =
        component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext());
    assertThat(cachedLayout).isNotNull();

    LithoNode result = Layout.create(layoutStateContext, c, component);
    assertThat(result).isEqualTo(cachedLayout);
    assertThat(component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext()))
        .isNull();

    assertThat(LifecycleStep.getSteps(steps)).doesNotContain(LifecycleStep.ON_CREATE_LAYOUT);
  }

  @Test
  public void testResolveLayoutUsesWillRenderResult() {
    ComponentContext c = new ComponentContext(getApplicationContext());
    final LayoutStateContext layoutStateContext = LayoutStateContext.getTestInstance(c);
    c.setLayoutStateContext(layoutStateContext); // TODO: To be deleted

    final Component component =
        TestLayoutComponent.create(c, 0, 0, true, true, false).key("global_key").build();

    c = ComponentContext.withComponentScope(layoutStateContext, c, component, "global_key");

    Component.willRender(c, component);

    final LithoNode cachedLayout =
        component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext());
    assertThat(cachedLayout).isNotNull();

    LithoNode result = Layout.create(layoutStateContext, c, component);
    assertThat(result).isEqualTo(cachedLayout);
    assertThat(component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext()))
        .isNull();
  }

  @Test
  public void testNewLayoutBuilderUsesWillRenderResult() {
    ComponentContext c = new ComponentContext(getApplicationContext());
    final LayoutStateContext layoutStateContext = LayoutStateContext.getTestInstance(c);
    c.setLayoutStateContext(layoutStateContext); // TODO: To be deleted

    final Component component =
        TestLayoutComponent.create(c, 0, 0, true, true, false).key("global_key").build();

    c = ComponentContext.withComponentScope(layoutStateContext, c, component, "global_key");

    Component.willRender(c, component);

    final LithoNode cachedLayout =
        component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext());
    assertThat(cachedLayout).isNotNull();

    LithoNode result = Layout.create(layoutStateContext, c, component);
    assertThat(result).isEqualTo(cachedLayout);
    assertThat(component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext()))
        .isNull();
  }

  @Test
  public void testCreateLayoutUsesWillRenderResult() {
    ComponentContext c = new ComponentContext(getApplicationContext());
    final LayoutStateContext layoutStateContext = LayoutStateContext.getTestInstance(c);
    c.setLayoutStateContext(layoutStateContext); // TODO: To be deleted

    final Component component =
        TestLayoutComponent.create(c, 0, 0, true, true, false).key("global_key").build();

    c = ComponentContext.withComponentScope(layoutStateContext, c, component, "global_key");

    Component.willRender(c, component);

    final LithoNode cachedLayout =
        component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext());
    assertThat(cachedLayout).isNotNull();

    LithoNode result = Layout.create(layoutStateContext, c, component);
    assertThat(result).isEqualTo(cachedLayout);
    assertThat(component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext()))
        .isNull();
  }

  @Test
  public void testWillRenderLayoutsOnceInColumn() {
    ComponentContext c = new ComponentContext(getApplicationContext());

    final Component componentSpy =
        spy(TestLayoutComponent.create(c, 0, 0, true, true, false).build());

    final Component root =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            Component.willRender(c, componentSpy);

            return Column.create(c).child(componentSpy).build();
          }
        };

    calculateLayoutState(
        mLegacyLithoViewRule.getComponentTree().getContext(),
        root,
        -1,
        makeSizeSpec(100, EXACTLY),
        makeSizeSpec(100, EXACTLY));
    verify(componentSpy, times(1)).render((ComponentContext) any(), eq(0), eq(0));
  }

  @Test
  public void testWillRenderTwiceDoesNotReCreateLayout() {
    ComponentContext c = new ComponentContext(getApplicationContext());
    final LayoutStateContext layoutStateContext = LayoutStateContext.getTestInstance(c);
    c.setLayoutStateContext(layoutStateContext); // TODO: To be deleted

    final Component component = TestLayoutComponent.create(c, 0, 0, true, true, false).build();

    Component.willRender(c, component);

    final LithoNode cachedLayout =
        component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext());
    assertThat(cachedLayout).isNotNull();

    assertThat(Component.willRender(c, component)).isTrue();
    assertThat(component.getLayoutCreatedInWillRender(layoutStateContext.getRenderStateContext()))
        .isEqualTo(cachedLayout);
  }

  @Test
  public void testComponentsLoggerCanReturnNullPerfEventsDuringLayout() {
    final Component component =
        new InlineLayoutSpec() {
          @Override
          protected Component onCreateLayout(final ComponentContext c) {
            return create(c).child(TestDrawableComponent.create(c)).wrapInView().build();
          }
        };

    final ComponentsLogger logger =
        new TestComponentsLogger() {
          @Override
          public @Nullable PerfEvent newPerformanceEvent(ComponentContext c, int eventId) {
            return null;
          }
        };

    final LayoutState layoutState =
        LayoutState.calculate(
            ComponentTree.create(mLegacyLithoViewRule.getContext())
                .logger(logger, "test")
                .build()
                .getContext(),
            component,
            -1,
            makeSizeSpec(100, EXACTLY),
            makeSizeSpec(100, EXACTLY),
            LayoutState.CalculateLayoutSource.TEST);

    assertThat(layoutState.getMountableOutputCount()).isEqualTo(3);
  }

  @Test
  public void whenAccessibleChildNodeExists_ParentNodeShouldImplementVirtualViews() {
    enableAccessibility();

    final Component component = Text.create(mContext).text("hello world").build();

    mLegacyLithoViewRule.setRoot(component).attachToWindow().measure().layout();

    final ComponentHost innerHost =
        (ComponentHost) mLegacyLithoViewRule.getLithoView().getChildAt(0);

    assertThat(innerHost.implementsVirtualViews())
        .describedAs("The parent output of the Text must implement virtual views")
        .isTrue();
  }

  @Test
  public void whenNoAccessibleChildNodeExists_ParentNodeShouldNotImplementVirtualViews() {
    enableAccessibility();

    final Component component = SolidColor.create(mContext).color(Color.BLACK).build();

    mLegacyLithoViewRule.setRoot(component).attachToWindow().measure().layout();

    assertThat(mLegacyLithoViewRule.getLithoView().implementsVirtualViews())
        .describedAs("The parent output of the drawable must not implement virtual views")
        .isFalse();
  }

  @Test
  public void onMountItemUpdatesImplementVirtualViews_ComponentHostShouldAlsoUpdate() {
    enableAccessibility();

    mLegacyLithoViewRule
        .setRoot(Text.create(mContext).text("hello world").build())
        .attachToWindow()
        .measure()
        .layout();

    final ComponentHost innerHost =
        (ComponentHost) mLegacyLithoViewRule.getLithoView().getChildAt(0);

    assertThat(innerHost.implementsVirtualViews())
        .describedAs("The parent output of the Text must implement virtual views")
        .isTrue();

    mLegacyLithoViewRule
        .setRootAndSizeSpecSync(
            SolidColor.create(mContext).color(Color.BLACK).build(),
            SizeSpec.makeSizeSpec(100, EXACTLY),
            SizeSpec.makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    assertThat(mLegacyLithoViewRule.getLithoView().implementsVirtualViews())
        .describedAs("The parent output of the drawable must not implement virtual views")
        .isFalse();

    mLegacyLithoViewRule
        .setRootAndSizeSpecSync(
            Column.create(mContext)
                .child(Text.create(mContext).text("hello world").build())
                .child(SolidColor.create(mContext).color(Color.BLACK).build())
                .build(),
            SizeSpec.makeSizeSpec(100, EXACTLY),
            SizeSpec.makeSizeSpec(200, EXACTLY))
        .attachToWindow()
        .measure()
        .layout();

    assertThat(mLegacyLithoViewRule.getLithoView().implementsVirtualViews())
        .describedAs("The root output must not implement virtual views")
        .isFalse();

    final ComponentHost host = (ComponentHost) mLegacyLithoViewRule.getLithoView().getChildAt(0);
    assertThat(host.implementsVirtualViews())
        .describedAs("The parent output of the Text must implement virtual views")
        .isTrue();
  }

  @Test
  public void onMountHierarchyWithParentDisabled_shouldDisableDescendants() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final ItemCardComponentSpec.TreeProps props = new ItemCardComponentSpec.TreeProps();
    final Output<View> view = new Output<>();
    final Output<Boolean> clicked = new Output<>();
    clicked.set(false);
    props.onCardActionViewVisible =
        new Function<Void>() {

          @Override
          public @Nullable Void call(@Nullable Object... arguments) {
            assertThat(arguments).isNotNull();
            assertThat(arguments).isNotEmpty();
            view.set((View) arguments[0]);
            return null;
          }
        };

    props.onCardActionsTouched =
        new Function<Void>() {
          @Override
          public @Nullable Void call(@Nullable Object... arguments) {
            clicked.set(true);
            return null;
          }
        };

    props.areCardToolsDisabled = true;

    final Component root =
        ItemCardComponent.create(c).body(Text.create(c).text("hello").build()).id(1).build();

    mLegacyLithoViewRule
        .setTreeProp(ItemCardComponentSpec.TreeProps.class, props)
        .setRoot(root)
        .attachToWindow()
        .measure()
        .layout();

    assertThat(view.get()).isNotNull();
    assertThat(view.get().isEnabled()).isFalse();
  }

  @Test
  public void onLayoutCreateInMeasure_shouldBeReusedDuringLayoutIfCompatibleMeasures() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final Component component =
        ComponentCaching.create(c)
            .component(
                LayoutSpecLifecycleTester.create(c).widthPx(100).heightPx(100).steps(steps).build())
            .widthSpec(MeasureSpecUtils.exactly(100))
            .heightSpec(MeasureSpecUtils.exactly(100))
            .build();

    mLegacyLithoViewRule.attachToWindow().setRoot(component).layout().measure();

    assertThat(LifecycleStep.getSteps(steps)).containsOnlyOnce(LifecycleStep.ON_CREATE_LAYOUT);
  }

  @Test
  public void onLayoutCreateInMeasure_shouldBeNotReusedDuringLayoutIfIncompatibleMeasures() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final Component component =
        ComponentCaching.create(c)
            .component(LayoutSpecLifecycleTester.create(c).steps(steps).build())
            .widthSpec(MeasureSpecUtils.exactly(100))
            .heightSpec(MeasureSpecUtils.exactly(100))
            .build();

    mLegacyLithoViewRule.attachToWindow().setRoot(component).layout().measure();

    assertThat(LifecycleStep.getSteps(steps))
        .contains(LifecycleStep.ON_CREATE_LAYOUT, LifecycleStep.ON_CREATE_LAYOUT);
  }

  private void enableAccessibility() {
    final ShadowAccessibilityManager manager =
        Shadows.shadowOf(
            (AccessibilityManager) getApplicationContext().getSystemService(ACCESSIBILITY_SERVICE));
    manager.setEnabled(true);
    manager.setTouchExplorationEnabled(true);
  }

  private LayoutState calculateLayoutState(
      final ComponentContext context,
      final Component component,
      final int componentTreeId,
      final int widthSpec,
      final int heightSpec) {

    return LayoutState.calculate(
        context,
        component,
        componentTreeId,
        widthSpec,
        heightSpec,
        LayoutState.CalculateLayoutSource.TEST);
  }

  @After
  public void restoreConfiguration() {
    TempComponentsConfigurations.restoreShouldAddHostViewForRootComponent();
  }

  private static long getHostId(RenderTreeNode node) {
    return node.getParent() != null ? node.getParent().getRenderUnit().getId() : 0;
  }
}
