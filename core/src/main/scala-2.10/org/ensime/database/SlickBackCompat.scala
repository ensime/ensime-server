// Copyright: 2010 - 2017 https://github.com/ensime/ensime-server/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.database

object SlickBackCompat {
  def h2Api = slick.driver.H2Driver.api
}
