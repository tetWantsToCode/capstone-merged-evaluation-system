import { useState, useMemo } from 'react';

export const usePagination = (data, pageSize = 10) => {
  const [currentPage, setCurrentPage] = useState(1);

  const paginatedData = useMemo(() => {
    // If data changes and current page becomes invalid, reset to 1
    const totalPages = Math.ceil((data?.length || 0) / pageSize);
    let validPage = currentPage;
    if (currentPage > totalPages && totalPages > 0) {
      validPage = totalPages;
      setCurrentPage(validPage);
    }

    const start = (validPage - 1) * pageSize;
    return (data || []).slice(start, start + pageSize);
  }, [currentPage, pageSize, data]);

  const totalPages = Math.ceil((data?.length || 0) / pageSize);

  const goToPage = (page) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page);
    }
  };

  return {
    currentPage,
    totalPages,
    paginatedData,
    goToPage
  };
};
