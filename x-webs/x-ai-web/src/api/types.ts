export interface BaseResult<T> {
  code: number;
  msg: string;
  data: T;
}

export interface PageData {
  page: number;
  pageTotal: number;
  pageSize: number;
  hasNextPages: boolean;
}

export interface PageResult<T> {
  pageData: PageData;
  results: T;
}
