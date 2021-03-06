# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Make sure we get the class.
java.lang.Throwable

class ::Java::JavaLang::Throwable
  def self.===(another)
    (another.kind_of?(::JavaUtilities::JavaException) &&
     another.java_exception.kind_of?(self)) ||
      super(another)
  end
end
