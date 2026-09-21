package sage;

/**
 * Small, side-effect-free DVD control rules shared by VideoFrame and the
 * server-side Java/Ogle player. Keeping these rules independent makes their
 * compatibility behavior directly testable without constructing a UI or DVD
 * reader session.
 */
final class DVDPlaybackControl
{
  private static final int DVD_CONTROL_ACTIVATE_CURRENT = 208;
  private static final int TITLE_DOMAIN = 4;

  private DVDPlaybackControl()
  {
  }

  /** Selects the VM-owned button after a NAV or program-chain transition. */
  static int currentNavButton(int vmButton, int previouslyRenderedButton)
  {
    return vmButton > 0 ? vmButton : previouslyRenderedButton;
  }

  /** Prevents Skip Back near title start from passing a negative PTS to the VM. */
  static long clampDiscSeekTime(long requestedTime)
  {
    return Math.max(0, requestedTime);
  }

  /**
   * An authored menu button may execute SetSTN to select audio and subtitle
   * streams. Once that happens, SageTV's default-language selection must not
   * overwrite the explicit choice when the VM enters title domain.
   */
  static boolean menuActivationOwnsLanguageSelections(int controlCode, int domain)
  {
    return controlCode == DVD_CONTROL_ACTIVATE_CURRENT && domain != TITLE_DOMAIN;
  }
}
