package sage;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DVDPlaybackControlTest
{
  @Test
  public void navTransitionUsesVmButtonInsteadOfStaleRenderedButton()
  {
    assertEquals(DVDPlaybackControl.currentNavButton(1, 2), 1);
    assertEquals(DVDPlaybackControl.currentNavButton(0, 2), 2);
  }

  @Test
  public void discSeekNeverPassesNegativeTimeToDvdVm()
  {
    assertEquals(DVDPlaybackControl.clampDiscSeekTime(-30000), 0);
    assertEquals(DVDPlaybackControl.clampDiscSeekTime(0), 0);
    assertEquals(DVDPlaybackControl.clampDiscSeekTime(12500), 12500);
  }

  @Test
  public void authoredMenuActivationPreservesDvdVmLanguageSelection()
  {
    assertTrue(DVDPlaybackControl.menuActivationOwnsLanguageSelections(208, 2));
    assertTrue(DVDPlaybackControl.menuActivationOwnsLanguageSelections(208, 3));
    assertFalse(DVDPlaybackControl.menuActivationOwnsLanguageSelections(208, 4));
    assertFalse(DVDPlaybackControl.menuActivationOwnsLanguageSelections(210, 3));
  }
}
