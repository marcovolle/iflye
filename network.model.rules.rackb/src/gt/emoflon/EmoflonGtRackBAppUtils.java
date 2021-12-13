package gt.emoflon;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import gt.emoflon.apps.EmoflonGtRackBHiPEApp;
import network.model.rules.rackb.api.RackbAPI;

/**
 * Utility class for the Rules Apps.
 *
 * Parts of this implementation are heavily inspired, taken or adapted from the
 * idyve project [1].
 *
 * [1] Tomaszek, S., Modellbasierte Einbettung von virtuellen Netzwerken in
 * Rechenzentren, http://dx.doi.org/10.12921/TUPRINTS-00017362. – DOI
 * 10.12921/TUPRINTS– 00017362, 2020.
 *
 * @author Stefan Tomaszek (ES TU Darmstadt) [idyve project]
 * @author Maximilian Kratz {@literal <maximilian.kratz@es.tu-darmstadt.de>}
 */
public class EmoflonGtRackBAppUtils {

	/**
	 * Private constructor forbids instantiation of objects.
	 */
	private EmoflonGtRackBAppUtils() {
	}

	/**
	 * Extracts the specified 'ibex-patterns.xmi' file if not already present.
	 *
	 * @param workspacePath The path of the workspace.
	 */
	public static void extractFiles(final String workspacePath) {
		final File target = new File(workspacePath + RackbAPI.patternPath);
		if (target.exists()) {
			return;
		}
		try (final InputStream is = EmoflonGtRackBHiPEApp.class
				.getResourceAsStream("/network/model/rules/rackb/api/ibex-patterns.xmi")) {
			target.getParentFile().mkdirs();
			if (is == null) {
				throw new IllegalStateException("ibex-patterns are missing from the resources");
			}
			Files.copy(is, target.toPath());
			target.deleteOnExit();
		} catch (final IOException e) {
			throw new IllegalStateException("Something went wrong while copying emoflon resources", e);
		}
	}

}
