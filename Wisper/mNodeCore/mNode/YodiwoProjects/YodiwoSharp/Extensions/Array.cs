﻿using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;


namespace Yodiwo
{
    public static partial class Extensions
    {
        //----------------------------------------------------------------------------------------------------------------------------------------------

        public static T[] CloneStrong<T>(this T[] array)
        {
            return array.Clone() as T[];
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------

        public static bool ArraysEqual<T>(this T[] a1, T[] a2)
        {
            if (ReferenceEquals(a1, a2))
                return true;

            if (a1 == null || a2 == null)
                return false;

            if (a1.Length != a2.Length)
                return false;

            EqualityComparer<T> comparer = EqualityComparer<T>.Default;
            for (int i = 0; i < a1.Length; i++)
            {
                if (!comparer.Equals(a1[i], a2[i])) return false;
            }
            return true;
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------

        public static bool ArraysEqual<T>(this T[] a1, T[] a2, int length)
        {
            if (ReferenceEquals(a1, a2))
                return true;

            if (a1 == null || a2 == null)
                return false;

            if (a1.Length < length || a2.Length < length)
                return false;

            EqualityComparer<T> comparer = EqualityComparer<T>.Default;
            for (int i = 0; i < length; i++)
            {
                if (!comparer.Equals(a1[i], a2[i])) return false;
            }
            return true;
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
    }
}
