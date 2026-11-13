// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';

import router from './index';

describe('application routes', () => {
  it('uses the generic runtime for every application identifier', () => {
    for (const id of ['color-lab', 'http-lab', 'personal-application']) {
      const route = router.resolve(`/apps/${id}`);
      expect(route.name).toBe('app-dynamic');
      expect(route.params.appId).toBe(id);
    }
  });
});
